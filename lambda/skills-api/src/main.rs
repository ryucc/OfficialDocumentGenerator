use aws_sdk_dynamodb::types::AttributeValue;
use aws_sdk_dynamodb::Client as DynamoClient;
use aws_sdk_s3::Client as S3Client;
use lambda_http::{
    http::Method, run, service_fn, Body, Error, Request, RequestExt, Response,
};
use serde::Serialize;
use std::collections::HashMap;
use std::env;

#[derive(Clone)]
struct AppState {
    dynamo: DynamoClient,
    s3: S3Client,
    table_name: String,
    skills_bucket: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct Skill {
    skill_id: String,
    name: String,
    display_name: String,
    description: String,
    owner: String,
    language: String,
    version: String,
    schema_json: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SkillDetail {
    #[serde(flatten)]
    skill: Skill,
    instructions_md: String,
    instructions_html: String,
}

#[derive(Serialize)]
struct ListResponse {
    items: Vec<Skill>,
    count: usize,
}

#[derive(Serialize)]
struct ErrorResponse {
    error: String,
}

const CORS_HEADERS: [(&str, &str); 4] = [
    ("Access-Control-Allow-Origin", "*"),
    ("Access-Control-Allow-Headers", "Content-Type,Authorization"),
    ("Access-Control-Allow-Methods", "GET,OPTIONS"),
    ("Content-Type", "application/json"),
];

fn build_response(status: u16, body: &impl Serialize) -> Result<Response<Body>, Error> {
    let json = serde_json::to_string(body)?;
    let mut builder = Response::builder().status(status);
    for (key, value) in CORS_HEADERS {
        builder = builder.header(key, value);
    }
    Ok(builder.body(Body::from(json))?)
}

fn options_response() -> Result<Response<Body>, Error> {
    let mut builder = Response::builder().status(200);
    for (key, value) in CORS_HEADERS {
        builder = builder.header(key, value);
    }
    Ok(builder.body(Body::Empty)?)
}

fn str_field(item: &HashMap<String, AttributeValue>, key: &str) -> String {
    item.get(key)
        .and_then(|v| v.as_s().ok())
        .cloned()
        .unwrap_or_default()
}

fn from_dynamo_item(item: &HashMap<String, AttributeValue>) -> Option<Skill> {
    Some(Skill {
        skill_id: item.get("skillId")?.as_s().ok()?.clone(),
        name: str_field(item, "name"),
        display_name: str_field(item, "displayName"),
        description: str_field(item, "description"),
        owner: str_field(item, "owner"),
        language: str_field(item, "language"),
        version: str_field(item, "version"),
        schema_json: item.get("schemaJson").and_then(|v| v.as_s().ok()).cloned(),
        created_at: str_field(item, "createdAt"),
        updated_at: str_field(item, "updatedAt"),
    })
}

fn render_markdown(md: &str) -> String {
    use pulldown_cmark::{html, Parser};
    let parser = Parser::new(md);
    let mut html_output = String::new();
    html::push_html(&mut html_output, parser);
    html_output
}

async fn list_skills(state: &AppState) -> Result<Response<Body>, Error> {
    let result = state
        .dynamo
        .scan()
        .table_name(&state.table_name)
        .send()
        .await?;

    let mut skills: Vec<Skill> = result
        .items()
        .iter()
        .filter_map(|item| from_dynamo_item(item))
        .collect();

    skills.sort_by(|a, b| b.created_at.cmp(&a.created_at));

    let count = skills.len();
    build_response(200, &ListResponse { items: skills, count })
}

async fn get_skill(state: &AppState, skill_id: &str) -> Result<Response<Body>, Error> {
    let result = state
        .dynamo
        .get_item()
        .table_name(&state.table_name)
        .key("skillId", AttributeValue::S(skill_id.to_string()))
        .send()
        .await?;

    let Some(item) = result.item() else {
        return build_response(404, &ErrorResponse { error: "Skill not found".into() });
    };

    let Some(skill) = from_dynamo_item(item) else {
        return build_response(500, &ErrorResponse { error: "Failed to parse skill".into() });
    };

    // Load instructions markdown from S3
    let instructions_md = if !state.skills_bucket.is_empty() {
        match state
            .s3
            .get_object()
            .bucket(&state.skills_bucket)
            .key(format!("skills/{}/instructions.md", skill_id))
            .send()
            .await
        {
            Ok(output) => {
                match output.body.collect().await {
                    Ok(b) => String::from_utf8(b.into_bytes().to_vec()).unwrap_or_default(),
                    Err(_) => String::new(),
                }
            }
            Err(e) => {
                tracing::warn!("Failed to load instructions for {}: {}", skill_id, e);
                String::new()
            }
        }
    } else {
        String::new()
    };

    let instructions_html = render_markdown(&instructions_md);

    build_response(200, &SkillDetail {
        skill,
        instructions_md,
        instructions_html,
    })
}

async fn handler(state: &AppState, event: Request) -> Result<Response<Body>, Error> {
    let method = event.method().clone();
    let path = event.raw_http_path().to_string();
    tracing::info!(%method, %path, "Handling request");

    match method {
        Method::OPTIONS => options_response(),
        Method::GET => {
            // Check if this is a detail request: /api/v1/skills/{skillId}
            let skill_id = event.path_parameters().first("skillId").map(String::from);
            if let Some(skill_id) = skill_id {
                get_skill(state, &skill_id).await
            } else {
                list_skills(state).await
            }
        }
        _ => build_response(
            405,
            &ErrorResponse {
                error: "Method not allowed".to_string(),
            },
        ),
    }
}

#[tokio::main]
async fn main() -> Result<(), Error> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .json()
        .without_time()
        .init();

    let config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let dynamo = DynamoClient::new(&config);
    let s3 = S3Client::new(&config);
    let table_name = env::var("SKILLS_TABLE").expect("SKILLS_TABLE must be set");
    let skills_bucket = env::var("SKILLS_BUCKET").unwrap_or_default();

    let state = AppState { dynamo, s3, table_name, skills_bucket };

    run(service_fn(|event| handler(&state, event))).await
}
