use aws_sdk_dynamodb::types::AttributeValue;
use aws_sdk_dynamodb::Client as DynamoClient;
use lambda_http::{
    http::Method, run, service_fn, Body, Error, Request, RequestExt, Response,
};
use serde::Serialize;
use std::env;

#[derive(Clone)]
struct AppState {
    dynamo: DynamoClient,
    users_table: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct InstalledSkillsResponse {
    user_id: String,
    installed_skills: Vec<String>,
}

#[derive(Serialize)]
struct MessageResponse {
    message: String,
}

#[derive(Serialize)]
struct ErrorResponse {
    error: String,
}

const CORS_HEADERS: [(&str, &str); 4] = [
    ("Access-Control-Allow-Origin", "*"),
    ("Access-Control-Allow-Headers", "Content-Type,Authorization"),
    ("Access-Control-Allow-Methods", "GET,PUT,DELETE,OPTIONS"),
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

/// Extract path parameters from /api/v1/users/{userId}/skills/{skillId}
fn extract_params(event: &Request) -> (Option<String>, Option<String>) {
    let params = event.path_parameters();
    let user_id = params.first("userId").map(String::from);
    let skill_id = params.first("skillId").map(String::from);
    (user_id, skill_id)
}

/// GET /api/v1/users/{userId}/skills
async fn list_installed_skills(
    state: &AppState,
    user_id: &str,
) -> Result<Response<Body>, Error> {
    let result = state
        .dynamo
        .get_item()
        .table_name(&state.users_table)
        .key("userId", AttributeValue::S(user_id.to_string()))
        .projection_expression("installedSkills")
        .send()
        .await?;

    let skills = result
        .item()
        .and_then(|item| item.get("installedSkills"))
        .and_then(|v| v.as_ss().ok())
        .cloned()
        .unwrap_or_default();

    build_response(
        200,
        &InstalledSkillsResponse {
            user_id: user_id.to_string(),
            installed_skills: skills,
        },
    )
}

/// PUT /api/v1/users/{userId}/skills/{skillId}
async fn install_skill(
    state: &AppState,
    user_id: &str,
    skill_id: &str,
) -> Result<Response<Body>, Error> {
    state
        .dynamo
        .update_item()
        .table_name(&state.users_table)
        .key("userId", AttributeValue::S(user_id.to_string()))
        .update_expression("ADD installedSkills :skill")
        .expression_attribute_values(
            ":skill",
            AttributeValue::Ss(vec![skill_id.to_string()]),
        )
        .send()
        .await?;

    build_response(
        200,
        &MessageResponse {
            message: format!("Skill {} installed", skill_id),
        },
    )
}

/// DELETE /api/v1/users/{userId}/skills/{skillId}
async fn uninstall_skill(
    state: &AppState,
    user_id: &str,
    skill_id: &str,
) -> Result<Response<Body>, Error> {
    state
        .dynamo
        .update_item()
        .table_name(&state.users_table)
        .key("userId", AttributeValue::S(user_id.to_string()))
        .update_expression("DELETE installedSkills :skill")
        .expression_attribute_values(
            ":skill",
            AttributeValue::Ss(vec![skill_id.to_string()]),
        )
        .send()
        .await?;

    build_response(
        200,
        &MessageResponse {
            message: format!("Skill {} uninstalled", skill_id),
        },
    )
}

async fn handler(state: &AppState, event: Request) -> Result<Response<Body>, Error> {
    let method = event.method().clone();
    let (user_id, skill_id) = extract_params(&event);

    tracing::info!(%method, ?user_id, ?skill_id, "Handling request");

    match method {
        Method::OPTIONS => options_response(),
        Method::GET => {
            let Some(user_id) = user_id else {
                return build_response(400, &ErrorResponse { error: "Missing userId".into() });
            };
            list_installed_skills(state, &user_id).await
        }
        Method::PUT => {
            let (Some(user_id), Some(skill_id)) = (user_id, skill_id) else {
                return build_response(400, &ErrorResponse { error: "Missing userId or skillId".into() });
            };
            install_skill(state, &user_id, &skill_id).await
        }
        Method::DELETE => {
            let (Some(user_id), Some(skill_id)) = (user_id, skill_id) else {
                return build_response(400, &ErrorResponse { error: "Missing userId or skillId".into() });
            };
            uninstall_skill(state, &user_id, &skill_id).await
        }
        _ => build_response(405, &ErrorResponse { error: "Method not allowed".into() }),
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
    let users_table = env::var("USERS_TABLE").expect("USERS_TABLE must be set");

    let state = AppState { dynamo, users_table };

    run(service_fn(|event| handler(&state, event))).await
}
