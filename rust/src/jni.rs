//! JNI bindings for Android.
//!
//! These functions are called from Kotlin via JNI.

use crate::epub::parse_epub;
use crate::types::{Book, Chapter, ChapterStats, Word};
use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jobject, jstring};
use jni::JNIEnv;

/// Parse an EPUB file and return a Book object.
///
/// Kotlin signature: external fun parseEpub(data: ByteArray): Book?
#[no_mangle]
pub extern "system" fn Java_app_spread_data_NativeParser_parseEpub<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: JByteArray<'local>,
) -> jobject {
    // Convert Java byte array to Rust slice
    let data_vec = match env.convert_byte_array(&data) {
        Ok(v) => v,
        Err(_) => return std::ptr::null_mut(),
    };

    // Parse EPUB
    let book = match parse_epub(&data_vec) {
        Ok(b) => b,
        Err(e) => {
            // Log error (in real app, would use Android logging)
            eprintln!("EPUB parse error: {}", e);
            return std::ptr::null_mut();
        }
    };

    // Convert to Java objects
    match book_to_jobject(&mut env, &book) {
        Ok(obj) => obj.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Get parser version for debugging
#[no_mangle]
pub extern "system" fn Java_app_spread_data_NativeParser_getVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let version = env.new_string("0.1.0").expect("Failed to create string");
    version.into_raw()
}

// --- Helper functions to convert Rust types to Java objects ---

fn book_to_jobject<'local>(
    env: &mut JNIEnv<'local>,
    book: &Book,
) -> Result<JObject<'local>, jni::errors::Error> {
    // Create BookMetadata
    let title = env.new_string(&book.metadata.title)?;
    let author = match &book.metadata.author {
        Some(a) => env.new_string(a)?,
        None => JString::default(),
    };

    let metadata_class = env.find_class("app/spread/data/NativeBookMetadata")?;
    let metadata = env.new_object(
        metadata_class,
        "(Ljava/lang/String;Ljava/lang/String;)V",
        &[JValue::Object(&title), JValue::Object(&author)],
    )?;

    // Create chapters array
    let chapter_class = env.find_class("app/spread/data/NativeChapter")?;
    let chapters_array =
        env.new_object_array(book.chapters.len() as i32, &chapter_class, JObject::null())?;

    for (i, chapter) in book.chapters.iter().enumerate() {
        let chapter_obj = chapter_to_jobject(env, chapter)?;
        env.set_object_array_element(&chapters_array, i as i32, chapter_obj)?;
    }

    // Create BookStats
    let stats = stats_to_jobject(env, &book.stats.aggregated, book.stats.total_words)?;

    // Create Book
    let book_class = env.find_class("app/spread/data/NativeBook")?;
    let book_obj = env.new_object(
        book_class,
        "(Lapp/spread/data/NativeBookMetadata;[Lapp/spread/data/NativeChapter;Lapp/spread/data/NativeBookStats;)V",
        &[
            JValue::Object(&metadata),
            JValue::Object(&chapters_array),
            JValue::Object(&stats),
        ],
    )?;

    Ok(book_obj)
}

fn chapter_to_jobject<'local>(
    env: &mut JNIEnv<'local>,
    chapter: &Chapter,
) -> Result<JObject<'local>, jni::errors::Error> {
    let title = env.new_string(&chapter.title)?;

    // Create words array
    let word_class = env.find_class("app/spread/data/NativeWord")?;
    let words_array =
        env.new_object_array(chapter.words.len() as i32, &word_class, JObject::null())?;

    for (i, word) in chapter.words.iter().enumerate() {
        let word_obj = word_to_jobject(env, word)?;
        env.set_object_array_element(&words_array, i as i32, word_obj)?;
    }

    // Create stats
    let stats = chapter_stats_to_jobject(env, &chapter.stats)?;

    let chapter_class = env.find_class("app/spread/data/NativeChapter")?;
    let chapter_obj = env.new_object(
        chapter_class,
        "(ILjava/lang/String;[Lapp/spread/data/NativeWord;Lapp/spread/data/NativeChapterStats;)V",
        &[
            JValue::Int(chapter.index as i32),
            JValue::Object(&title),
            JValue::Object(&words_array),
            JValue::Object(&stats),
        ],
    )?;

    Ok(chapter_obj)
}

fn word_to_jobject<'local>(
    env: &mut JNIEnv<'local>,
    word: &Word,
) -> Result<JObject<'local>, jni::errors::Error> {
    let text = env.new_string(&word.text)?;

    let word_class = env.find_class("app/spread/data/NativeWord")?;
    let word_obj = env.new_object(
        word_class,
        "(Ljava/lang/String;II)V",
        &[
            JValue::Object(&text),
            JValue::Int(word.length_bucket as i32),
            JValue::Int(word.following_punct as i32),
        ],
    )?;

    Ok(word_obj)
}

fn chapter_stats_to_jobject<'local>(
    env: &mut JNIEnv<'local>,
    stats: &ChapterStats,
) -> Result<JObject<'local>, jni::errors::Error> {
    // Create arrays first to avoid borrow checker issues
    let length_arr = int_array_to_jobject(env, &stats.length_counts)?;
    let punct_arr = int_array_to_jobject(env, &stats.punct_counts)?;

    let stats_class = env.find_class("app/spread/data/NativeChapterStats")?;
    let stats_obj = env.new_object(
        stats_class,
        "(I[I[I)V",
        &[
            JValue::Int(stats.word_count as i32),
            JValue::Object(&length_arr),
            JValue::Object(&punct_arr),
        ],
    )?;

    Ok(stats_obj)
}

fn stats_to_jobject<'local>(
    env: &mut JNIEnv<'local>,
    stats: &ChapterStats,
    total_words: u32,
) -> Result<JObject<'local>, jni::errors::Error> {
    let chapter_stats = chapter_stats_to_jobject(env, stats)?;

    let stats_class = env.find_class("app/spread/data/NativeBookStats")?;
    let stats_obj = env.new_object(
        stats_class,
        "(ILapp/spread/data/NativeChapterStats;)V",
        &[
            JValue::Int(total_words as i32),
            JValue::Object(&chapter_stats),
        ],
    )?;

    Ok(stats_obj)
}

fn int_array_to_jobject<'local>(
    env: &mut JNIEnv<'local>,
    arr: &[u32; 4],
) -> Result<JObject<'local>, jni::errors::Error> {
    let int_arr = env.new_int_array(4)?;
    let vals: [i32; 4] = [arr[0] as i32, arr[1] as i32, arr[2] as i32, arr[3] as i32];
    env.set_int_array_region(&int_arr, 0, &vals)?;
    Ok(JObject::from(int_arr))
}
