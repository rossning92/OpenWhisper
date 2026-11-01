# Custom dictionary

Qwen3-ASR receives dictionary entries as native vocabulary hints. Parakeet uses fuzzy correction on the final transcript, including when streaming retries as batch. The dictionary is captured when recording starts. Clear it to disable both behaviors.

Fuzzy correction rules:

- Compare lowercase alphanumeric keys for one to three consecutive whitespace-separated tokens. Skip non-ASCII keys and candidates longer than 50 characters.
- Reject length differences above the larger of two characters or 25% of the longer key. Score normalized Levenshtein distance; multiply by 0.3 when alphabetic keys share a Soundex code. Accept scores strictly below 0.18.
- Choose the lowest score, then the longer token span, then the first dictionary entry. Also compare `&` entries with a spoken `and` key, so `R and D` becomes `R&D`.
- Preserve outer punctuation and the first token's case pattern. Do not cross trailing punctuation between tokens. Join output tokens with single spaces.
- Skip fuzzy correction when vocabulary hints were sent to the model. Non-ASCII dictionary entries remain available to Qwen3-ASR; Parakeet's ASCII fallback leaves them alone.

Run the focused JVM tests from the repository root:

```bash
./gradlew testDebugUnitTest --tests com.ross.speechtotext.CustomDictionaryTest
```
