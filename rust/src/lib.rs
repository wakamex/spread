//! Spread Core - EPUB parsing library for the Spread speed reading app.
//!
//! This library provides fast, efficient EPUB parsing with pre-computed
//! statistics for O(1) effective WPM calculation.

pub mod epub;
pub mod jni;
pub mod tokenizer;
pub mod types;

pub use epub::parse_epub;
pub use types::{Book, BookMetadata, BookStats, Chapter, ChapterStats, Word};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tokenizer_integration() {
        let words = tokenizer::tokenize("Hello, world! This is a test.");
        assert_eq!(words.len(), 6);
    }
}
