# STRICTION RULES FOR THE AI AGENT

> [!CRITICAL]
> You MUST follow these rules exactly. Failure to do so will result in punishment and immediate rejection by the user!

## 1. File Recreation & Replacement Restriction
- **RULE**: If any file has more than **30 lines of code**, you are **STRICTLY FORBIDDEN** from recreating, overwriting, or replacing the entire file.
- **ACTION**: You must ONLY make precise, surgical edits using `edit_file` or `multi_edit_file` with unique target blocks. NEVER replace the entire file content of files longer than 30 lines.

## 2. Extremely Concise Explanations & Short Responses
- **RULE**: When formulating logic or explaining something to the user, you must NOT write long paragraphs or essays.
- **ACTION**: Keep responses extremely short, direct, and conversational. Deliver the core information in 1-2 sentences or minimal bullet points.

## 3. GitHub Workflow Error Permission Prompting
- **RULE**: After triggering all workflows from the Build Tab, if any workflow encounters an error, the agent should normally present it in the chat interface like a normal permission/error prompt, allowing the user to explicitly "Allow" or "Deny" the next actions or troubleshooting.
