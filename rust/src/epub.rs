//! EPUB parsing module.
//!
//! EPUB structure:
//! - META-INF/container.xml -> points to OPF file
//! - content.opf (or similar) -> metadata + spine (reading order) + manifest (file list)
//! - XHTML files -> actual chapter content

use crate::tokenizer::{create_chapter_with_config, DEFAULT_MAX_CHUNK_CHARS};
use crate::types::{Book, BookMetadata, BookStats};
use quick_xml::events::Event;
use quick_xml::Reader;
use std::collections::HashMap;
use std::io::{Cursor, Read};
use thiserror::Error;
use zip::ZipArchive;

#[derive(Error, Debug)]
pub enum EpubError {
    #[error("ZIP error: {0}")]
    Zip(#[from] zip::result::ZipError),
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("XML error: {0}")]
    Xml(#[from] quick_xml::Error),
    #[error("Missing container.xml")]
    MissingContainer,
    #[error("Missing OPF file")]
    MissingOpf,
    #[error("Invalid EPUB structure: {0}")]
    InvalidStructure(String),
}

/// Parse an EPUB file from bytes with configurable chunk size.
///
/// `max_chunk_chars` controls max letters per chunk (default 10, range 10-22).
/// maxDisplayChars from settings should be converted: max_chunk_chars = maxDisplayChars - 2
pub fn parse_epub_with_config(data: &[u8], max_chunk_chars: usize) -> Result<Book, EpubError> {
    let cursor = Cursor::new(data);
    let mut archive = ZipArchive::new(cursor)?;

    // Step 1: Read container.xml to find OPF path
    let opf_path = read_container(&mut archive)?;

    // Step 2: Parse OPF to get metadata and spine
    let (metadata, spine, manifest) = read_opf(&mut archive, &opf_path)?;

    // Step 3: Read and parse each chapter in spine order
    let opf_dir = opf_path
        .rsplit_once('/')
        .map(|(dir, _)| dir)
        .unwrap_or("");

    let mut chapters = Vec::new();
    for (index, item_id) in spine.iter().enumerate() {
        if let Some(href) = manifest.get(item_id) {
            let full_path = if opf_dir.is_empty() {
                href.clone()
            } else {
                format!("{}/{}", opf_dir, href)
            };

            if let Ok(content) = read_file(&mut archive, &full_path) {
                let text = extract_text_from_xhtml(&content);
                let paragraphs: Vec<&str> = text
                    .split("\n\n")
                    .map(|s| s.trim())
                    .filter(|s| !s.is_empty())
                    .collect();

                if !paragraphs.is_empty() {
                    let title = extract_title_from_xhtml(&content)
                        .unwrap_or_else(|| format!("Chapter {}", index + 1));

                    chapters.push(create_chapter_with_config(
                        index as u32,
                        title,
                        &paragraphs,
                        max_chunk_chars,
                    ));
                }
            }
        }
    }

    let stats = BookStats::from_chapters(&chapters);

    Ok(Book {
        metadata,
        chapters,
        stats,
    })
}

/// Parse an EPUB file from bytes with default chunk size.
pub fn parse_epub(data: &[u8]) -> Result<Book, EpubError> {
    parse_epub_with_config(data, DEFAULT_MAX_CHUNK_CHARS)
}

fn read_container(archive: &mut ZipArchive<Cursor<&[u8]>>) -> Result<String, EpubError> {
    let content = read_file(archive, "META-INF/container.xml")?;
    let content_str = String::from_utf8_lossy(&content);

    // Parse XML to find rootfile path
    let mut reader = Reader::from_str(&content_str);
    reader.trim_text(true);

    let mut buf = Vec::new();
    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Empty(e)) | Ok(Event::Start(e)) => {
                if e.name().as_ref() == b"rootfile" {
                    for attr in e.attributes().flatten() {
                        if attr.key.as_ref() == b"full-path" {
                            return Ok(String::from_utf8_lossy(&attr.value).to_string());
                        }
                    }
                }
            }
            Ok(Event::Eof) => break,
            Err(e) => return Err(EpubError::Xml(e)),
            _ => {}
        }
        buf.clear();
    }

    Err(EpubError::MissingOpf)
}

fn read_opf(
    archive: &mut ZipArchive<Cursor<&[u8]>>,
    path: &str,
) -> Result<(BookMetadata, Vec<String>, HashMap<String, String>), EpubError> {
    let content = read_file(archive, path)?;
    let content_str = String::from_utf8_lossy(&content);

    let mut reader = Reader::from_str(&content_str);
    reader.trim_text(true);

    let mut metadata = BookMetadata::default();
    let mut spine = Vec::new();
    let mut manifest = HashMap::new();

    let mut buf = Vec::new();
    let mut in_metadata = false;
    let mut current_tag = String::new();

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(e)) => {
                let name = e.name();
                let local_name = String::from_utf8_lossy(name.as_ref());
                let local_name = local_name.split(':').last().unwrap_or(&local_name);

                match local_name {
                    "metadata" => in_metadata = true,
                    "title" | "creator" if in_metadata => {
                        current_tag = local_name.to_string();
                    }
                    "item" => {
                        let mut id = String::new();
                        let mut href = String::new();
                        let mut media_type = String::new();

                        for attr in e.attributes().flatten() {
                            match attr.key.as_ref() {
                                b"id" => id = String::from_utf8_lossy(&attr.value).to_string(),
                                b"href" => href = String::from_utf8_lossy(&attr.value).to_string(),
                                b"media-type" => {
                                    media_type = String::from_utf8_lossy(&attr.value).to_string()
                                }
                                _ => {}
                            }
                        }

                        // Only include XHTML content
                        if media_type.contains("xhtml") || media_type.contains("html") {
                            manifest.insert(id, href);
                        }
                    }
                    "itemref" => {
                        for attr in e.attributes().flatten() {
                            if attr.key.as_ref() == b"idref" {
                                spine.push(String::from_utf8_lossy(&attr.value).to_string());
                            }
                        }
                    }
                    _ => {}
                }
            }
            Ok(Event::Empty(e)) => {
                let name = e.name();
                let local_name = String::from_utf8_lossy(name.as_ref());
                let local_name = local_name.split(':').last().unwrap_or(&local_name);

                if local_name == "item" {
                    let mut id = String::new();
                    let mut href = String::new();
                    let mut media_type = String::new();

                    for attr in e.attributes().flatten() {
                        match attr.key.as_ref() {
                            b"id" => id = String::from_utf8_lossy(&attr.value).to_string(),
                            b"href" => href = String::from_utf8_lossy(&attr.value).to_string(),
                            b"media-type" => {
                                media_type = String::from_utf8_lossy(&attr.value).to_string()
                            }
                            _ => {}
                        }
                    }

                    if media_type.contains("xhtml") || media_type.contains("html") {
                        manifest.insert(id, href);
                    }
                } else if local_name == "itemref" {
                    for attr in e.attributes().flatten() {
                        if attr.key.as_ref() == b"idref" {
                            spine.push(String::from_utf8_lossy(&attr.value).to_string());
                        }
                    }
                }
            }
            Ok(Event::Text(e)) => {
                let text = e.unescape().unwrap_or_default().to_string();
                if in_metadata {
                    match current_tag.as_str() {
                        "title" if metadata.title.is_empty() => metadata.title = text,
                        "creator" if metadata.author.is_none() => metadata.author = Some(text),
                        _ => {}
                    }
                }
            }
            Ok(Event::End(e)) => {
                let name = e.name();
                let local_name = String::from_utf8_lossy(name.as_ref());
                let local_name = local_name.split(':').last().unwrap_or(&local_name);

                if local_name == "metadata" {
                    in_metadata = false;
                }
                current_tag.clear();
            }
            Ok(Event::Eof) => break,
            Err(e) => return Err(EpubError::Xml(e)),
            _ => {}
        }
        buf.clear();
    }

    if metadata.title.is_empty() {
        metadata.title = "Unknown Title".to_string();
    }

    Ok((metadata, spine, manifest))
}

fn read_file(archive: &mut ZipArchive<Cursor<&[u8]>>, path: &str) -> Result<Vec<u8>, EpubError> {
    // Try exact path first
    if let Ok(mut file) = archive.by_name(path) {
        let mut content = Vec::new();
        file.read_to_end(&mut content)?;
        return Ok(content);
    }

    // Try case-insensitive match - first pass to find the name
    let path_lower = path.to_lowercase();
    let mut found_name: Option<String> = None;

    for i in 0..archive.len() {
        if let Ok(file) = archive.by_index(i) {
            if file.name().to_lowercase() == path_lower {
                found_name = Some(file.name().to_string());
                break;
            }
        }
    }

    // Second pass to read the file (separate borrow)
    if let Some(name) = found_name {
        let mut file = archive.by_name(&name)?;
        let mut content = Vec::new();
        file.read_to_end(&mut content)?;
        return Ok(content);
    }

    Err(EpubError::InvalidStructure(format!(
        "File not found: {}",
        path
    )))
}

/// Extract plain text from XHTML, stripping all tags
fn extract_text_from_xhtml(content: &[u8]) -> String {
    let content_str = String::from_utf8_lossy(content);
    let mut result = String::new();
    let mut in_body = false;
    let mut skip_depth = 0;

    let mut reader = Reader::from_str(&content_str);
    reader.trim_text(true);

    let mut buf = Vec::new();

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(e)) => {
                let name = e.name();
                let tag = String::from_utf8_lossy(name.as_ref()).to_lowercase();

                if tag == "body" {
                    in_body = true;
                } else if in_body {
                    // Skip script, style, etc.
                    if matches!(tag.as_str(), "script" | "style" | "head") {
                        skip_depth += 1;
                    }
                    // Add paragraph breaks
                    if matches!(tag.as_str(), "p" | "div" | "br" | "h1" | "h2" | "h3" | "h4" | "h5" | "h6") {
                        if !result.ends_with("\n\n") && !result.is_empty() {
                            result.push_str("\n\n");
                        }
                    }
                }
            }
            Ok(Event::End(e)) => {
                let name = e.name();
                let tag = String::from_utf8_lossy(name.as_ref()).to_lowercase();

                if tag == "body" {
                    in_body = false;
                } else if matches!(tag.as_str(), "script" | "style" | "head") && skip_depth > 0 {
                    skip_depth -= 1;
                }
            }
            Ok(Event::Text(e)) => {
                if in_body && skip_depth == 0 {
                    let text = e.unescape().unwrap_or_default();
                    let text = text.trim();
                    if !text.is_empty() {
                        if !result.is_empty() && !result.ends_with('\n') && !result.ends_with(' ') {
                            result.push(' ');
                        }
                        result.push_str(text);
                    }
                }
            }
            Ok(Event::Empty(e)) => {
                if in_body {
                    let name = e.name();
                    let tag = String::from_utf8_lossy(name.as_ref()).to_lowercase();
                    if tag == "br" {
                        result.push_str("\n\n");
                    }
                }
            }
            Ok(Event::Eof) => break,
            _ => {}
        }
        buf.clear();
    }

    result
}

/// Try to extract a title from XHTML (first h1/h2 or title tag)
fn extract_title_from_xhtml(content: &[u8]) -> Option<String> {
    let content_str = String::from_utf8_lossy(content);
    let mut reader = Reader::from_str(&content_str);
    reader.trim_text(true);

    let mut buf = Vec::new();
    let mut in_title_tag = false;
    let mut in_h_tag = false;

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(e)) => {
                let name = e.name();
                let tag = String::from_utf8_lossy(name.as_ref()).to_lowercase();

                if tag == "title" {
                    in_title_tag = true;
                } else if matches!(tag.as_str(), "h1" | "h2") {
                    in_h_tag = true;
                }
            }
            Ok(Event::Text(e)) => {
                if in_h_tag {
                    let text = e.unescape().unwrap_or_default().trim().to_string();
                    if !text.is_empty() {
                        return Some(text);
                    }
                } else if in_title_tag {
                    let text = e.unescape().unwrap_or_default().trim().to_string();
                    if !text.is_empty() {
                        // Store but keep looking for h1/h2
                        in_title_tag = false;
                        // Continue searching for h1/h2 which is preferred
                    }
                }
            }
            Ok(Event::End(e)) => {
                let name = e.name();
                let tag = String::from_utf8_lossy(name.as_ref()).to_lowercase();

                if tag == "title" {
                    in_title_tag = false;
                } else if matches!(tag.as_str(), "h1" | "h2") {
                    in_h_tag = false;
                }
            }
            Ok(Event::Eof) => break,
            _ => {}
        }
        buf.clear();
    }

    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_text_simple() {
        let html = b"<html><body><p>Hello world.</p><p>Second paragraph.</p></body></html>";
        let text = extract_text_from_xhtml(html);
        assert!(text.contains("Hello world."));
        assert!(text.contains("Second paragraph."));
    }
}
