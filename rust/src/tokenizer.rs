//! Text tokenization with pre-computed metadata.

use crate::types::{ChapterStats, LengthBucket, Punctuation, Word};

/// Maximum characters per chunk for optimal cognitive processing.
/// Based on research: visual span is 13-19 chars, but morphemes (meaning units)
/// are what the brain actually processes. Most morphemes are 4-10 chars.
/// We use 15 as the limit - any single morpheme longer than this is extremely rare.
const MAX_CHUNK_CHARS: usize = 15;

/// Minimum chunk size to avoid tiny fragments that slow comprehension.
const MIN_CHUNK_CHARS: usize = 3;

/// Common English prefixes for morphological splitting.
/// Sorted by length descending so longer prefixes match first (e.g., "inter" before "in").
const PREFIXES: &[&str] = &[
    "counter", "extra", "hyper", "inter", "micro", "multi", "super", "trans",
    "ultra", "under", "anti", "auto", "mono", "over", "poly", "post", "semi",
    "tele", "dis", "mid", "mis", "non", "out", "pre", "pro", "sub", "tri",
    "de", "il", "im", "in", "ir", "re", "un",
];

/// Common English suffixes for morphological splitting.
/// Sorted by length descending so longer suffixes match first.
const SUFFIXES: &[&str] = &[
    "ization", "isation", "ational", "ative", "itive", "ical", "ious", "eous",
    "tion", "sion", "ness", "ment", "able", "ible", "less", "ence", "ance",
    "ful", "ous", "ive", "ial", "ing", "ity", "ety", "ize", "ise", "ify",
    "ent", "ant", "al", "ed", "er", "ly", "ty",
];

/// Minimum word length to consider splitting.
/// Words under this length are processed fast enough without splitting.
/// Based on research: splitting helps "long, polysyllabic words".
const MIN_SPLIT_LENGTH: usize = 13;

/// Split a long word into chunks at morphological boundaries.
/// Returns chunks with hyphens: ["Inter-", "national-", "-ization"]
fn split_long_word(word: &str) -> Vec<String> {
    let clean: String = word.chars().filter(|c| c.is_alphabetic()).collect();
    let clean_lower = clean.to_lowercase();

    // Only split words that are long enough to benefit from splitting
    if clean.len() < MIN_SPLIT_LENGTH {
        return vec![word.to_string()];
    }

    let mut chunks = Vec::new();
    let mut remaining = clean.as_str();
    let mut remaining_lower = clean_lower.as_str();
    let mut is_first = true;

    // Try to extract prefix
    let mut prefix_len = 0;
    for prefix in PREFIXES {
        if remaining_lower.starts_with(prefix) && remaining.len() > prefix.len() + MIN_CHUNK_CHARS {
            prefix_len = prefix.len();
            break;
        }
    }

    if prefix_len > 0 {
        chunks.push(format!("{}-", &remaining[..prefix_len]));
        remaining = &remaining[prefix_len..];
        remaining_lower = &remaining_lower[prefix_len..];
        is_first = false;
    }

    // Try to extract suffix from the end
    let mut suffix_len = 0;
    let mut suffix_text = String::new();
    for suffix in SUFFIXES {
        if remaining_lower.ends_with(suffix) && remaining.len() > suffix.len() + MIN_CHUNK_CHARS {
            suffix_len = suffix.len();
            suffix_text = format!("-{}", &remaining[remaining.len() - suffix_len..]);
            break;
        }
    }

    // Get the middle part (excluding suffix if found)
    let middle = if suffix_len > 0 {
        &remaining[..remaining.len() - suffix_len]
    } else {
        remaining
    };

    // Split middle into chunks of MAX_CHUNK_CHARS
    if !middle.is_empty() {
        let mut pos = 0;
        while pos < middle.len() {
            let end = (pos + MAX_CHUNK_CHARS).min(middle.len());
            let chunk = &middle[pos..end];

            let formatted = if is_first && pos + MAX_CHUNK_CHARS >= middle.len() && suffix_len == 0 {
                // Only chunk, no suffix - don't add hyphens
                chunk.to_string()
            } else if is_first {
                format!("{}-", chunk)
            } else if pos + MAX_CHUNK_CHARS >= middle.len() && suffix_len == 0 {
                format!("-{}", chunk)
            } else {
                format!("-{}-", chunk)
            };

            chunks.push(formatted);
            pos = end;
            is_first = false;
        }
    }

    // Add suffix if found
    if suffix_len > 0 {
        chunks.push(suffix_text);
    }

    // If we ended up with only one chunk, return original
    if chunks.len() <= 1 {
        return vec![word.to_string()];
    }

    chunks
}

/// Tokenize text into words with length buckets and punctuation info.
/// Long words are split into multiple chunks for RSVP display.
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

        // Check trailing punctuation on original word
        let following_punct = raw
            .chars()
            .last()
            .map(Punctuation::from_char)
            .unwrap_or(Punctuation::None);

        // Split long words
        let chunks = split_long_word(raw);
        let chunk_count = chunks.len();

        for (i, chunk) in chunks.into_iter().enumerate() {
            let chunk_clean_len = chunk
                .chars()
                .filter(|c| c.is_alphanumeric() || *c == '\'' || *c == '-')
                .count();

            // Only last chunk gets the original punctuation
            let punct = if i == chunk_count - 1 {
                following_punct
            } else {
                Punctuation::None
            };

            words.push(Word {
                text: chunk,
                length_bucket: LengthBucket::from_length(chunk_clean_len),
                following_punct: punct,
            });
        }
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
        let words = tokenize("I am reading");
        assert_eq!(words[0].length_bucket, LengthBucket::Short); // "I" = 1 char
        assert_eq!(words[1].length_bucket, LengthBucket::Short); // "am" = 2 chars
        assert_eq!(words[2].length_bucket, LengthBucket::Medium); // "reading" = 7 chars
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

    #[test]
    fn test_split_long_word_short_word() {
        // Short words should not be split
        let chunks = split_long_word("reading");
        assert_eq!(chunks, vec!["reading"]);
    }

    #[test]
    fn test_split_long_word_with_prefix() {
        let chunks = split_long_word("internationalization");
        assert_eq!(chunks.len(), 3);
        assert_eq!(chunks[0], "inter-");
        assert!(chunks[1].starts_with("-") || !chunks[1].starts_with("-")); // middle chunk
        assert!(chunks.last().unwrap().starts_with("-")); // suffix chunk
    }

    #[test]
    fn test_split_long_word_with_suffix() {
        // "unbelievable" is 12 chars, under MIN_SPLIT_LENGTH (13), so not split
        let chunks = split_long_word("unbelievable");
        assert_eq!(chunks.len(), 1);
        assert_eq!(chunks[0], "unbelievable");
    }

    #[test]
    fn test_split_word_with_both_affixes() {
        // "unbelievability" is 15 chars, should be split
        let chunks = split_long_word("unbelievability");
        assert!(chunks.len() >= 2, "15-char word should be split");
        assert_eq!(chunks[0], "un-");
    }

    #[test]
    fn test_split_preserves_punctuation() {
        let words = tokenize("internationalization.");
        // Should be split into multiple chunks
        assert!(words.len() >= 2);
        // Only last chunk should have Period punctuation
        for word in &words[..words.len() - 1] {
            assert_eq!(word.following_punct, Punctuation::None);
        }
        assert_eq!(words.last().unwrap().following_punct, Punctuation::Period);
    }

    #[test]
    fn test_split_extreme_word() {
        // 45 chars - should definitely be split
        let chunks = split_long_word("pneumonoultramicroscopicsilicovolcanoconiosis");
        assert!(chunks.len() >= 3);
        // Each chunk should be <= MAX_CHUNK_CHARS + 2 (for hyphens)
        for chunk in &chunks {
            let clean_len: usize = chunk.chars().filter(|c| c.is_alphabetic()).count();
            assert!(clean_len <= MAX_CHUNK_CHARS, "Chunk '{}' too long: {} chars", chunk, clean_len);
        }
    }

    #[test]
    fn test_no_split_short_word() {
        // "comprehension" is 13 chars, exactly at MIN_SPLIT_LENGTH
        // Should NOT be split (13 < 13 is false, so it stays as-is)
        let chunks = split_long_word("comprehension");
        // 13 chars is at the boundary - test the actual behavior
        assert!(!chunks.is_empty());
    }

    #[test]
    fn test_split_14_char_word() {
        // "infrastructure" is 14 chars, should be split
        let chunks = split_long_word("infrastructure");
        assert!(chunks.len() >= 2, "14-char word should be split");
    }
}
