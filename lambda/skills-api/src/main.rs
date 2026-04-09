use aws_sdk_dynamodb::types::AttributeValue;
use aws_sdk_dynamodb::Client as DynamoClient;
use lambda_http::{
    http::Method, run, service_fn, Body, Error, Request, Response,
};
use serde::Serialize;
use std::collections::HashMap;
use std::env;

#[derive(Clone)]
struct AppState {
    dynamo: DynamoClient,
    table_name: String,
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

async fn handler(state: &AppState, event: Request) -> Result<Response<Body>, Error> {
    let method = event.method().clone();
    tracing::info!(%method, "Handling request");

    match method {
        Method::OPTIONS => options_response(),
        Method::GET => list_skills(state).await,
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
    let table_name = env::var("SKILLS_TABLE").expect("SKILLS_TABLE must be set");

    let state = AppState { dynamo, table_name };

    run(service_fn(|event| handler(&state, event))).await
}
