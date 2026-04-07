use aws_sdk_dynamodb::types::AttributeValue;
use lambda_http::{Body, Error, Response};
use serde::Deserialize;
use std::collections::HashMap;
use std::time::Duration;

use crate::{error_response, json_response, AppState, ListResponse, SampleDocument};

const STATUS_AVAILABLE: &str = "AVAILABLE";
const STATUS_PENDING_UPLOAD: &str = "PENDING_UPLOAD";
const URL_EXPIRY_SECS: u64 = 900; // 15 minutes

// ── List Documents ──────────────────────────────────────────────────────────

pub async fn list_documents(state: &AppState) -> Result<Response<Body>, Error> {
    let result = state
        .dynamo
        .scan()
        .table_name(&state.table_name)
        .send()
        .await?;

    let mut docs: Vec<SampleDocument> = result
        .items()
        .iter()
        .filter_map(|item| from_dynamo_item(item))
        .filter(|doc| doc.status == STATUS_AVAILABLE)
        .collect();

    docs.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));

    json_response(200, &ListResponse { items: docs })
}

// ── Get Document ────────────────────────────────────────────────────────────

pub async fn get_document(state: &AppState, id: &str) -> Result<Response<Body>, Error> {
    match find_document(state, id).await? {
        Some(doc) => json_response(200, &doc),
        None => error_response(404, "NOT_FOUND", "Document not found"),
    }
}

// ── Get Download URL ────────────────────────────────────────────────────────

pub async fn get_download_url(state: &AppState, id: &str) -> Result<Response<Body>, Error> {
    let doc = match find_document(state, id).await? {
        Some(doc) => doc,
        None => return error_response(404, "NOT_FOUND", "Document not found"),
    };

    if doc.status != STATUS_AVAILABLE {
        return error_response(409, "DOCUMENT_NOT_READY", "Document is not ready for download");
    }

    let source_key = match find_source_key(state, id).await? {
        Some(key) => key,
        None => {
            return error_response(
                409,
                "DOCUMENT_STORAGE_INCONSISTENT",
                "Document storage is inconsistent",
            )
        }
    };

    let config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let s3_presigner = aws_sdk_s3::presigning::PresigningConfig::builder()
        .expires_in(Duration::from_secs(URL_EXPIRY_SECS))
        .build()?;

    let bucket = std::env::var("UPLOADED_DOCUMENT_BUCKET").unwrap_or_default();
    let s3_client = aws_sdk_s3::Client::new(&config);

    let presigned = s3_client
        .get_object()
        .bucket(&bucket)
        .key(&source_key)
        .presigned(s3_presigner)
        .await?;

    json_response(
        200,
        &serde_json::json!({
            "downloadUrl": presigned.uri(),
            "downloadMethod": "GET",
            "expiresAt": chrono_expiry()
        }),
    )
}

// ── Create Document ─────────────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateRequest {
    pub filename: String,
    pub content_type: String,
    #[serde(default)]
    pub size_bytes: Option<i64>,
}

pub async fn create_document(
    state: &AppState,
    req: CreateRequest,
) -> Result<Response<Body>, Error> {
    let filename = req.filename.trim();
    let content_type = req.content_type.trim();

    if filename.is_empty() {
        return error_response(400, "BAD_REQUEST", "filename is required");
    }
    if content_type.is_empty() {
        return error_response(400, "BAD_REQUEST", "contentType is required");
    }
    if filename.contains('/') || filename.contains('\\') {
        return error_response(
            400,
            "BAD_REQUEST",
            "filename must not contain path separators",
        );
    }

    let id = uuid::Uuid::new_v4().to_string();
    let timestamp = chrono::Utc::now().to_rfc3339();
    let object_key = format!("sample-documents/{}/{}", id, filename);

    let mut item = HashMap::new();
    item.insert("documentId".into(), AttributeValue::S(id.clone()));
    item.insert("filename".into(), AttributeValue::S(filename.to_string()));
    item.insert(
        "contentType".into(),
        AttributeValue::S(content_type.to_string()),
    );
    item.insert(
        "status".into(),
        AttributeValue::S(STATUS_PENDING_UPLOAD.to_string()),
    );
    item.insert("sourceObjectKey".into(), AttributeValue::S(object_key.clone()));
    item.insert("createdAt".into(), AttributeValue::S(timestamp.clone()));
    item.insert("updatedAt".into(), AttributeValue::S(timestamp.clone()));
    if let Some(size) = req.size_bytes {
        item.insert("sizeBytes".into(), AttributeValue::N(size.to_string()));
    }

    state
        .dynamo
        .put_item()
        .table_name(&state.table_name)
        .set_item(Some(item))
        .send()
        .await?;

    // Generate presigned upload URL
    let config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let bucket = std::env::var("UPLOADED_DOCUMENT_BUCKET").unwrap_or_default();
    let s3_client = aws_sdk_s3::Client::new(&config);

    let presign_config = aws_sdk_s3::presigning::PresigningConfig::builder()
        .expires_in(Duration::from_secs(URL_EXPIRY_SECS))
        .build()?;

    let presigned = s3_client
        .put_object()
        .bucket(&bucket)
        .key(&object_key)
        .content_type(content_type)
        .presigned(presign_config)
        .await?;

    let doc = SampleDocument {
        id,
        filename: filename.to_string(),
        content_type: content_type.to_string(),
        size_bytes: req.size_bytes,
        status: STATUS_PENDING_UPLOAD.to_string(),
        created_at: timestamp.clone(),
        updated_at: timestamp,
    };

    json_response(
        201,
        &serde_json::json!({
            "document": doc,
            "upload": {
                "uploadUrl": presigned.uri(),
                "uploadMethod": "PUT",
                "uploadHeaders": { "Content-Type": content_type },
                "expiresAt": chrono_expiry()
            }
        }),
    )
}

// ── Complete Upload ─────────────────────────────────────────────────────────

pub async fn complete_upload(state: &AppState, id: &str) -> Result<Response<Body>, Error> {
    let doc = match find_document(state, id).await? {
        Some(doc) => doc,
        None => return error_response(404, "NOT_FOUND", "Document not found"),
    };

    if doc.status == STATUS_AVAILABLE {
        return json_response(200, &doc);
    }

    let source_key = match find_source_key(state, id).await? {
        Some(key) => key,
        None => {
            return error_response(409, "UPLOAD_NOT_FOUND", "Uploaded object was not found")
        }
    };

    // Get actual file size from S3
    let config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let bucket = std::env::var("UPLOADED_DOCUMENT_BUCKET").unwrap_or_default();
    let s3_client = aws_sdk_s3::Client::new(&config);

    let head = match s3_client
        .head_object()
        .bucket(&bucket)
        .key(&source_key)
        .send()
        .await
    {
        Ok(h) => h,
        Err(_) => {
            return error_response(409, "UPLOAD_NOT_FOUND", "Uploaded object was not found in S3")
        }
    };

    let size = head.content_length().unwrap_or(0);
    let timestamp = chrono::Utc::now().to_rfc3339();

    state
        .dynamo
        .update_item()
        .table_name(&state.table_name)
        .key("documentId", AttributeValue::S(id.to_string()))
        .update_expression("SET #status = :status, sizeBytes = :size, updatedAt = :ts")
        .expression_attribute_names("#status", "status")
        .expression_attribute_values(":status", AttributeValue::S(STATUS_AVAILABLE.to_string()))
        .expression_attribute_values(":size", AttributeValue::N(size.to_string()))
        .expression_attribute_values(":ts", AttributeValue::S(timestamp.clone()))
        .send()
        .await?;

    let updated = SampleDocument {
        id: id.to_string(),
        filename: doc.filename,
        content_type: doc.content_type,
        size_bytes: Some(size),
        status: STATUS_AVAILABLE.to_string(),
        created_at: doc.created_at,
        updated_at: timestamp,
    };

    json_response(200, &updated)
}

// ── Delete Document ─────────────────────────────────────────────────────────

pub async fn delete_document(state: &AppState, id: &str) -> Result<Response<Body>, Error> {
    let source_key = find_source_key(state, id).await?;

    state
        .dynamo
        .delete_item()
        .table_name(&state.table_name)
        .key("documentId", AttributeValue::S(id.to_string()))
        .send()
        .await?;

    if let Some(key) = source_key {
        let config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
        let bucket = std::env::var("UPLOADED_DOCUMENT_BUCKET").unwrap_or_default();
        let s3_client = aws_sdk_s3::Client::new(&config);

        let _ = s3_client
            .delete_object()
            .bucket(&bucket)
            .key(&key)
            .send()
            .await;
    }

    let mut builder = Response::builder().status(204);
    for (key, value) in crate::cors_headers() {
        builder = builder.header(key, value);
    }
    Ok(builder.body(Body::Empty)?)
}

// ── Helpers ─────────────────────────────────────────────────────────────────

fn from_dynamo_item(item: &HashMap<String, AttributeValue>) -> Option<SampleDocument> {
    Some(SampleDocument {
        id: item.get("documentId")?.as_s().ok()?.clone(),
        filename: item.get("filename")?.as_s().ok()?.clone(),
        content_type: item.get("contentType")?.as_s().ok()?.clone(),
        size_bytes: item
            .get("sizeBytes")
            .and_then(|v| v.as_n().ok())
            .and_then(|n| n.parse().ok()),
        status: item.get("status")?.as_s().ok()?.clone(),
        created_at: item.get("createdAt")?.as_s().ok()?.clone(),
        updated_at: item.get("updatedAt")?.as_s().ok()?.clone(),
    })
}

async fn find_document(
    state: &AppState,
    id: &str,
) -> Result<Option<SampleDocument>, aws_sdk_dynamodb::Error> {
    let result = state
        .dynamo
        .get_item()
        .table_name(&state.table_name)
        .key("documentId", AttributeValue::S(id.to_string()))
        .consistent_read(true)
        .send()
        .await?;

    Ok(result.item().and_then(from_dynamo_item))
}

async fn find_source_key(
    state: &AppState,
    id: &str,
) -> Result<Option<String>, aws_sdk_dynamodb::Error> {
    let result = state
        .dynamo
        .get_item()
        .table_name(&state.table_name)
        .key("documentId", AttributeValue::S(id.to_string()))
        .projection_expression("sourceObjectKey")
        .send()
        .await?;

    Ok(result
        .item()
        .and_then(|item| item.get("sourceObjectKey"))
        .and_then(|v| v.as_s().ok())
        .cloned())
}

fn chrono_expiry() -> String {
    (chrono::Utc::now() + chrono::Duration::seconds(URL_EXPIRY_SECS as i64)).to_rfc3339()
}
