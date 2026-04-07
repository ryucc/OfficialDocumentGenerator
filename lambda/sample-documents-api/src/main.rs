use aws_sdk_dynamodb::Client as DynamoClient;
use lambda_http::{
    http::Method, run, service_fn, Body, Error, Request, RequestExt, RequestPayloadExt, Response,
};
use serde::{Deserialize, Serialize};
use std::env;

mod routes;

#[derive(Clone)]
pub struct AppState {
    pub dynamo: DynamoClient,
    pub table_name: String,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SampleDocument {
    pub id: String,
    pub filename: String,
    pub content_type: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub size_bytes: Option<i64>,
    pub status: String,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Serialize)]
pub struct ListResponse {
    pub items: Vec<SampleDocument>,
}

#[derive(Debug, Serialize)]
pub struct ApiError {
    pub code: String,
    pub message: String,
}

fn cors_headers() -> [(String, String); 4] {
    [
        ("Access-Control-Allow-Origin".into(), "*".into()),
        (
            "Access-Control-Allow-Headers".into(),
            "Content-Type,Authorization".into(),
        ),
        (
            "Access-Control-Allow-Methods".into(),
            "GET,POST,DELETE,OPTIONS".into(),
        ),
        ("Content-Type".into(), "application/json".into()),
    ]
}

fn json_response(status: u16, body: &impl Serialize) -> Result<Response<Body>, Error> {
    let json = serde_json::to_string(body)?;
    let mut builder = Response::builder().status(status);
    for (key, value) in cors_headers() {
        builder = builder.header(key, value);
    }
    Ok(builder.body(Body::from(json))?)
}

fn error_response(status: u16, code: &str, message: &str) -> Result<Response<Body>, Error> {
    json_response(
        status,
        &ApiError {
            code: code.to_string(),
            message: message.to_string(),
        },
    )
}

fn options_response() -> Result<Response<Body>, Error> {
    let mut builder = Response::builder().status(204);
    for (key, value) in cors_headers() {
        builder = builder.header(key, value);
    }
    Ok(builder.body(Body::Empty)?)
}

async fn handler(state: &AppState, event: Request) -> Result<Response<Body>, Error> {
    let method = event.method().clone();
    let path = event.uri().path().to_string();

    tracing::info!(%method, %path, "Handling request");

    if method == Method::OPTIONS {
        return options_response();
    }

    // Extract documentId from path params
    let doc_id = event
        .path_parameters()
        .first("documentId")
        .map(String::from);

    match method {
        Method::GET => {
            if let Some(id) = &doc_id {
                if path.ends_with("/download-url") {
                    routes::get_download_url(state, id).await
                } else {
                    routes::get_document(state, id).await
                }
            } else {
                routes::list_documents(state).await
            }
        }
        Method::POST => {
            if let Some(id) = &doc_id {
                if path.ends_with("/complete") {
                    routes::complete_upload(state, id).await
                } else {
                    error_response(404, "NOT_FOUND", "Not found")
                }
            } else {
                let body = event.payload::<routes::CreateRequest>()?;
                match body {
                    Some(req) => routes::create_document(state, req).await,
                    None => error_response(400, "BAD_REQUEST", "Request body is required"),
                }
            }
        }
        Method::DELETE => match &doc_id {
            Some(id) => routes::delete_document(state, id).await,
            None => error_response(400, "BAD_REQUEST", "documentId is required"),
        },
        _ => error_response(405, "METHOD_NOT_ALLOWED", "Method not allowed"),
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
    let table_name = env::var("UPLOADED_DOCUMENT_METADATA_TABLE")
        .expect("UPLOADED_DOCUMENT_METADATA_TABLE must be set");

    let state = AppState {
        dynamo,
        table_name,
    };

    run(service_fn(|event| handler(&state, event))).await
}
