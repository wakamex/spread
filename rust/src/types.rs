//! Core types for the Spread parser.
//! These mirror the Kotlin domain types.

/// Length bucket for adaptive timing
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum LengthBucket {
    Short = 0,     // 1-4 chars
    Medium = 1,    // 5-8 chars
    Long = 2,      // 9-12 chars
    VeryLong = 3,  // 13+ chars
}

impl LengthBucket {
    pub fn from_length(len: usize) -> Self {
        match len {
            0..=4 => LengthBucket::Short,
            5..=8 => LengthBucket::Medium,
            9..=12 => LengthBucket::Long,
            _ => LengthBucket::VeryLong,
        }
    }
}

/// Punctuation type for adaptive timing
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Punctuation {
    None = 0,
    Comma = 1,      // , ; :
    Period = 2,     // . ! ?
    Paragraph = 3,  // paragraph break
}

impl Punctuation {
    pub fn from_char(c: char) -> Self {
        match c {
            '.' | '!' | '?' => Punctuation::Period,
            ',' | ';' | ':' => Punctuation::Comma,
            _ => Punctuation::None,
        }
    }
}

/// A single word with pre-computed metadata for O(1) timing calculation
#[derive(Debug, Clone)]
pub struct Word {
    pub text: String,
    pub length_bucket: LengthBucket,
    pub following_punct: Punctuation,
}

/// Pre-computed statistics for a chapter (enables O(1) effective WPM calculation)
#[derive(Debug, Clone, Default)]
pub struct ChapterStats {
    pub word_count: u32,
    pub length_counts: [u32; 4],  // [short, medium, long, very_long]
    pub punct_counts: [u32; 4],   // [none, comma, period, paragraph]
}

impl ChapterStats {
    pub fn from_words(words: &[Word]) -> Self {
        let mut stats = ChapterStats::default();
        stats.word_count = words.len() as u32;

        for word in words {
            stats.length_counts[word.length_bucket as usize] += 1;
            stats.punct_counts[word.following_punct as usize] += 1;
        }

        stats
    }

    pub fn merge(&mut self, other: &ChapterStats) {
        self.word_count += other.word_count;
        for i in 0..4 {
            self.length_counts[i] += other.length_counts[i];
            self.punct_counts[i] += other.punct_counts[i];
        }
    }
}

/// A chapter in a book
#[derive(Debug, Clone)]
pub struct Chapter {
    pub index: u32,
    pub title: String,
    pub words: Vec<Word>,
    pub stats: ChapterStats,
}

/// Book metadata
#[derive(Debug, Clone, Default)]
pub struct BookMetadata {
    pub title: String,
    pub author: Option<String>,
}

/// Aggregated book statistics
#[derive(Debug, Clone, Default)]
pub struct BookStats {
    pub total_words: u32,
    pub aggregated: ChapterStats,
}

impl BookStats {
    pub fn from_chapters(chapters: &[Chapter]) -> Self {
        let mut aggregated = ChapterStats::default();
        for chapter in chapters {
            aggregated.merge(&chapter.stats);
        }
        BookStats {
            total_words: aggregated.word_count,
            aggregated,
        }
    }
}

/// A fully parsed book ready for the reader
#[derive(Debug, Clone)]
pub struct Book {
    pub metadata: BookMetadata,
    pub chapters: Vec<Chapter>,
    pub stats: BookStats,
}
