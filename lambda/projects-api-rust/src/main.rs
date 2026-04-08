use aws_sdk_dynamodb::types::AttributeValue;
use aws_sdk_dynamodb::Client as DynamoClient;
use lambda_http::{
    http::Method, run, service_fn, Body, Error, Request, RequestExt, Response,
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
struct Project {
    project_id: String,
    name: String,
    status: String,
    email_s3_key: String,
    email_s3_bucket: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    generated_document_s3_key: Option<String>,
    created_at: String,
    updated_at: String,
}

#[derive(Serialize)]
struct ListResponse {
    items: Vec<Project>,
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

fn from_dynamo_item(item: &HashMap<String, AttributeValue>) -> Option<Project> {
    Some(Project {
        project_id: item.get("projectId")?.as_s().ok()?.clone(),
        name: item.get("name").and_then(|v| v.as_s().ok()).cloned().unwrap_or_default(),
        status: item.get("status")?.as_s().ok()?.clone(),
        email_s3_key: item.get("emailS3Key").and_then(|v| v.as_s().ok()).cloned().unwrap_or_default(),
        email_s3_bucket: item.get("emailS3Bucket").and_then(|v| v.as_s().ok()).cloned().unwrap_or_default(),
        generated_document_s3_key: item.get("generatedDocumentS3Key").and_then(|v| v.as_s().ok()).cloned(),
        created_at: item.get("createdAt").and_then(|v| v.as_s().ok()).cloned().unwrap_or_default(),
        updated_at: item.get("updatedAt").and_then(|v| v.as_s().ok()).cloned().unwrap_or_default(),
    })
}

async fn list_projects(state: &AppState, status_filter: Option<&str>) -> Result<Response<Body>, Error> {
    let items = if let Some(status) = status_filter {
        // Query by status using GSI
        let result = state
            .dynamo
            .query()
            .table_name(&state.table_name)
            .index_name("StatusIndex")
            .key_condition_expression("#status = :status")
            .expression_attribute_names("#status", "status")
            .expression_attribute_values(":status", AttributeValue::S(status.to_string()))
            .send()
            .await?;
        result.items().to_vec()
    } else {
        // Scan all projects
        let result = state
            .dynamo
            .scan()
            .table_name(&state.table_name)
            .send()
            .await?;
        result.items().to_vec()
    };

    let mut projects: Vec<Project> = items.iter().filter_map(|item| from_dynamo_item(item)).collect();

    // Sort by createdAt descending
    projects.sort_by(|a, b| b.created_at.cmp(&a.created_at));

    let count = projects.len();
    build_response(200, &ListResponse { items: projects, count })
}

async fn handler(state: &AppState, event: Request) -> Result<Response<Body>, Error> {
    let method = event.method().clone();

    tracing::info!(%method, "Handling request");

    match method {
        Method::OPTIONS => options_response(),
        Method::GET => {
            let status_filter = event
                .query_string_parameters()
                .first("status")
                .map(String::from);
            list_projects(state, status_filter.as_deref()).await
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
    let table_name = env::var("PROJECTS_TABLE").expect("PROJECTS_TABLE must be set");

    let state = AppState { dynamo, table_name };

    run(service_fn(|event| handler(&state, event))).await
}
