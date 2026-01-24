//! Text tokenization with pre-computed metadata.

use crate::types::{ChapterStats, LengthBucket, Punctuation, Word};

/// Tokenize text into words with length buckets and punctuation info.
pub fn tokenize(text: &str) -> Vec<Word> {
    let mut words = Vec::new();

    for raw in text.split_whitespace() {
        if raw.is_empty() {
            continue;
        }

        // Count only alphanumeric chars for length bucket
        let clean_len = raw
            .chars()
            .filter(|c| c.is_alphanumeric() || *c == '\'' || *c == '-')
            .count();

        if clean_len == 0 {
            continue;
        }

        // Check trailing punctuation
        let following_punct = raw
            .chars()
            .last()
            .map(Punctuation::from_char)
            .unwrap_or(Punctuation::None);

        words.push(Word {
            text: raw.to_string(),
            length_bucket: LengthBucket::from_length(clean_len),
            following_punct,
        });
    }

    words
}

/// Tokenize multiple paragraphs, marking paragraph breaks.
pub fn tokenize_paragraphs(paragraphs: &[&str]) -> Vec<Word> {
    let mut all_words = Vec::new();
    let para_count = paragraphs.len();

    for (p_idx, para) in paragraphs.iter().enumerate() {
        let words = tokenize(para);
        if words.is_empty() {
            continue;
        }

        // Add all words except last
        if words.len() > 1 {
            all_words.extend_from_slice(&words[..words.len() - 1]);
        }

        // Mark last word with paragraph punctuation if not already marked
        let mut last_word = words.last().unwrap().clone();
        if p_idx < para_count - 1 && last_word.following_punct == Punctuation::None {
            last_word.following_punct = Punctuation::Paragraph;
        }
        all_words.push(last_word);
    }

    all_words
}

/// Create chapter from title and paragraphs
pub fn create_chapter(index: u32, title: String, paragraphs: &[&str]) -> crate::types::Chapter {
    let words = tokenize_paragraphs(paragraphs);
    let stats = ChapterStats::from_words(&words);

    crate::types::Chapter {
        index,
        title,
        words,
        stats,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tokenize_basic() {
        let words = tokenize("Hello, world!");
        assert_eq!(words.len(), 2);
        assert_eq!(words[0].text, "Hello,");
        assert_eq!(words[0].following_punct, Punctuation::Comma);
        assert_eq!(words[1].text, "world!");
        assert_eq!(words[1].following_punct, Punctuation::Period);
    }

    #[test]
    fn test_length_buckets() {
        let words = tokenize("I am extraordinary");
        assert_eq!(words[0].length_bucket, LengthBucket::Short); // "I" = 1 char
        assert_eq!(words[1].length_bucket, LengthBucket::Short); // "am" = 2 chars
        assert_eq!(words[2].length_bucket, LengthBucket::VeryLong); // "extraordinary" = 13 chars
    }

    #[test]
    fn test_paragraph_marking() {
        let paragraphs = vec!["First paragraph", "Second paragraph"];
        let words = tokenize_paragraphs(&paragraphs);

        // "paragraph" at end of first should have Paragraph punct
        assert_eq!(words[1].following_punct, Punctuation::Paragraph);
        // "paragraph" at end of second should not (it's the last)
        assert_eq!(words[3].following_punct, Punctuation::None);
    }
}
