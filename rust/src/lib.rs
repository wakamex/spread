//! Spread Core - EPUB parsing library for the Spread speed reading app.
//!
//! This library provides fast, efficient EPUB parsing with pre-computed
//! statistics for O(1) effective WPM calculation.

pub mod epub;
pub mod jni;
pub mod tokenizer;
pub mod types;

pub use epub::{parse_epub, parse_epub_with_config};
pub use types::{Book, BookMetadata, BookStats, Chapter, ChapterStats, Word};

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Instant;

    #[test]
    fn test_tokenizer_integration() {
        let words = tokenizer::tokenize("Hello, world! This is a test.");
        assert_eq!(words.len(), 6);
    }

    #[test]
    fn bench_parse_epub() {
        // Load test EPUB from fixtures
        let epub_path = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/fixtures/pride-and-prejudice.epub");
        let data = std::fs::read(epub_path)
            .expect("Test fixture not found - run: cp pride-and-prejudice.epub rust/tests/fixtures/");

        println!("\n=== EPUB Parsing Benchmark ===");
        println!("File: {} ({:.2} KB)", epub_path, data.len() as f64 / 1024.0);

        // Warm up
        let _ = parse_epub(&data);

        // Benchmark multiple runs
        let runs = 5;
        let mut times = Vec::new();

        for i in 0..runs {
            let start = Instant::now();
            let book = parse_epub(&data).expect("Parse failed");
            let elapsed = start.elapsed();
            times.push(elapsed);

            if i == 0 {
                println!("\nBook: {}", book.metadata.title);
                println!("Author: {}", book.metadata.author.as_deref().unwrap_or("Unknown"));
                println!("Chapters: {}", book.chapters.len());
                println!("Total words: {}", book.stats.total_words);
            }
        }

        let avg_ms = times.iter().map(|t| t.as_secs_f64() * 1000.0).sum::<f64>() / runs as f64;
        let min_ms = times.iter().map(|t| t.as_secs_f64() * 1000.0).fold(f64::INFINITY, f64::min);
        let max_ms = times.iter().map(|t| t.as_secs_f64() * 1000.0).fold(0.0, f64::max);

        println!("\nParsing times ({} runs):", runs);
        println!("  Min: {:.2}ms", min_ms);
        println!("  Max: {:.2}ms", max_ms);
        println!("  Avg: {:.2}ms", avg_ms);
        println!("==============================\n");
    }
}

#[test]
fn test_parse_demo_epub() {
    let epub_path = concat!(env!("CARGO_MANIFEST_DIR"), "/../app/src/main/assets/demo.epub");
    let data = std::fs::read(epub_path).expect("Failed to read demo.epub");
    
    let book = crate::epub::parse_epub(&data).expect("Failed to parse demo.epub");
    
    println!("Title: {}", book.metadata.title);
    println!("Author: {:?}", book.metadata.author);
    println!("Chapters: {}", book.chapters.len());
    for ch in &book.chapters {
        println!("  {} - {} words", ch.title, ch.words.len());
    }
    
    assert_eq!(book.metadata.title, "Understanding Speed Reading");
    assert_eq!(book.chapters.len(), 3);
}
