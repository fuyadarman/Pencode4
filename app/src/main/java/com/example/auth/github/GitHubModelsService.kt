package com.example.auth.github

data class GitHubModelInfo(
    val id: String,
    val name: String,
    val publisher: String,
    val description: String,
    val category: String, // Reasoning, Coding, Flagship, Fast
    val contextWindow: String = "128k"
)

object GitHubModelsService {

    const val GITHUB_MODELS_BASE_URL = "https://models.inference.ai.azure.com"
    const val GITHUB_MODELS_PROVIDER = "github_models"

    val AVAILABLE_MODELS = listOf(
        GitHubModelInfo(
            id = "gpt-4o",
            name = "GPT-4o",
            publisher = "OpenAI",
            description = "High-intelligence flagship model for complex multimodal tasks and coding.",
            category = "Flagship",
            contextWindow = "128k"
        ),
        GitHubModelInfo(
            id = "gpt-4o-mini",
            name = "GPT-4o Mini",
            publisher = "OpenAI",
            description = "Fast, affordable, lightweight model for everyday development tasks.",
            category = "Fast",
            contextWindow = "128k"
        ),
        GitHubModelInfo(
            id = "DeepSeek-R1",
            name = "DeepSeek R1",
            publisher = "DeepSeek",
            description = "State-of-the-art open reasoning model with transparent chain-of-thought.",
            category = "Reasoning",
            contextWindow = "64k"
        ),
        GitHubModelInfo(
            id = "Meta-Llama-3.3-70B-Instruct",
            name = "Llama 3.3 70B",
            publisher = "Meta",
            description = "Industry-standard open-weights powerhouse for reasoning and development.",
            category = "Flagship",
            contextWindow = "128k"
        ),
        GitHubModelInfo(
            id = "Phi-4",
            name = "Phi-4",
            publisher = "Microsoft",
            description = "Compact 14B model outperforming larger models in math and logic.",
            category = "Reasoning",
            contextWindow = "16k"
        ),
        GitHubModelInfo(
            id = "Mistral-large-2411",
            name = "Mistral Large",
            publisher = "Mistral AI",
            description = "Top-tier European reasoning and multilingual model.",
            category = "Flagship",
            contextWindow = "128k"
        ),
        GitHubModelInfo(
            id = "Mistral-small-2402",
            name = "Mistral Small",
            publisher = "Mistral AI",
            description = "Low-latency, highly optimized model for snappy responses.",
            category = "Fast",
            contextWindow = "32k"
        ),
        GitHubModelInfo(
            id = "Cohere-command-r-plus-08-2024",
            name = "Command R+",
            publisher = "Cohere",
            description = "Specialized for advanced tool use, RAG, and structured business logic.",
            category = "Coding",
            contextWindow = "128k"
        )
    )
}
