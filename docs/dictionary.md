# Custom dictionary

Qwen3-ASR receives dictionary entries as native vocabulary hints. Parakeet uses fuzzy correction on the final transcript, including when streaming retries as batch. The dictionary is captured when recording starts. Clear it to disable both behaviors.

Fuzzy correction rules:

- Compare lowercase alphanumeric keys for one to three consecutive whitespace-separated tokens. Skip non-ASCII keys and candidates longer than 50 characters.
- Reject length differences above the larger of two characters or 25% of the longer key. Score normalized Levenshtein distance; multiply by 0.3 when alphabetic keys share a Soundex code. Accept scores strictly below 0.18.
- Choose the lowest score, then the longer token span, then the first dictionary entry. Also compare `&` entries with a spoken `and` key, so `R and D` becomes `R&D`.
- Preserve outer punctuation and the first token's case pattern. Do not cross trailing punctuation between tokens. Join output tokens with single spaces.
- Skip fuzzy correction when vocabulary hints were sent to the model. Non-ASCII dictionary entries remain available to Qwen3-ASR; Parakeet's ASCII fallback leaves them alone.

## Updates from other apps

Send an explicit broadcast intent with action `com.ross.speechtotext.UPDATE_DICTIONARY`, string extra `text` (one entry per line), and optional string extra `mode`: `append` (default) or `replace`. Updates are applied immediately, without permission checks or confirmation. Any app can call this API.

From Termux:

```bash
am broadcast -a com.ross.speechtotext.UPDATE_DICTIONARY \
  -n com.ross.speechtotext/.DictionaryUpdateReceiver \
  --es mode append --es text "OpenAI
Qwen"
```

From an Android app:

```java
Intent intent = new Intent("com.ross.speechtotext.UPDATE_DICTIONARY")
        .setComponent(new ComponentName("com.ross.speechtotext",
                "com.ross.speechtotext.DictionaryUpdateReceiver"))
        .putExtra("mode", "append")
        .putExtra("text", "OpenAI\nQwen");
context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
        // getResultCode(): Activity.RESULT_OK (-1) means saved; 0 means failure.
        // getResultData(): status message, or null if no receiver handled the request.
    }
}, null, Activity.RESULT_CANCELED, null, null);
```

Termux prints `result=-1` and `data="Dictionary updated"` on success. Check this output: `am` exiting successfully only means the broadcast was sent. Unordered broadcasts also update the dictionary but provide no result.

Missing text, unsupported modes, and input over 32,000 UTF-16 code units are rejected. The resulting dictionary has the same limit. Blank lines and exact duplicates are removed; append preserves existing order. Replace with empty text clears the dictionary. Updates apply to the next recording. Open the app once after installation before using this API.

Run the focused JVM tests from the repository root:

```bash
./gradlew testDebugUnitTest --tests com.ross.speechtotext.CustomDictionaryTest
```
