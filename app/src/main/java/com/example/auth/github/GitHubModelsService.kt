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
        // NVIDIA
        GitHubModelInfo("nemotron-3-ultra", "Nemotron 3 Ultra", "NVIDIA", "Top-tier high intelligence reasoning and agentic model.", "Flagship", "128k"),
        GitHubModelInfo("nemotron-3-super", "Nemotron 3 Super", "NVIDIA", "Strong balanced reasoning and complex instruction following.", "Reasoning", "128k"),
        GitHubModelInfo("nemotron-3-nano:30b", "Nemotron 3 Nano 30B", "NVIDIA", "Fast, highly efficient model for coding and logic.", "Fast", "32k"),

        // DeepSeek
        GitHubModelInfo("DeepSeek-R1", "DeepSeek R1", "DeepSeek", "State-of-the-art open reasoning model with transparent chain-of-thought.", "Reasoning", "64k"),
        GitHubModelInfo("DeepSeek-V3", "DeepSeek V3", "DeepSeek", "671B MoE powerhouse for coding, mathematics, and multilingual reasoning.", "Flagship", "64k"),

        // OpenAI
        GitHubModelInfo("gpt-4o", "GPT-4o", "OpenAI", "Flagship intelligence for coding, architecture, and multi-turn workflows.", "Flagship", "128k"),
        GitHubModelInfo("gpt-4o-mini", "GPT-4o Mini", "OpenAI", "Fast, lightweight, affordable model for rapid development.", "Fast", "128k"),
        GitHubModelInfo("o1", "o1", "OpenAI", "Advanced reasoning model designed for complex logic, math, and code generation.", "Reasoning", "128k"),
        GitHubModelInfo("o1-mini", "o1-mini", "OpenAI", "Fast reasoning model optimized for coding and STEM disciplines.", "Reasoning", "128k"),
        GitHubModelInfo("o3-mini", "o3-mini", "OpenAI", "High-throughput specialized reasoning and programming engine.", "Reasoning", "128k"),
        GitHubModelInfo("chatgpt-4o-latest", "ChatGPT-4o Latest", "OpenAI", "Dynamically updated checkpoint of OpenAI's flagship model.", "Flagship", "128k"),

        // Meta Llama 3.3 & 3.2 & 3.1
        GitHubModelInfo("Meta-Llama-3.3-70B-Instruct", "Llama 3.3 70B", "Meta", "Industry-standard open powerhouse for software engineering.", "Flagship", "128k"),
        GitHubModelInfo("Llama-3.2-90B-Vision-Instruct", "Llama 3.2 90B Vision", "Meta", "Multimodal visual reasoning, architecture analysis, and code.", "Flagship", "128k"),
        GitHubModelInfo("Llama-3.2-11B-Vision-Instruct", "Llama 3.2 11B Vision", "Meta", "Compact multimodal model for image and code analysis.", "Fast", "128k"),
        GitHubModelInfo("Llama-3.2-3B-Instruct", "Llama 3.2 3B", "Meta", "Ultra-snappy lightweight model for instantaneous replies.", "Fast", "128k"),
        GitHubModelInfo("Llama-3.2-1B-Instruct", "Llama 3.2 1B", "Meta", "Extremely lightweight low-latency model.", "Fast", "128k"),
        GitHubModelInfo("Meta-Llama-3.1-405B-Instruct", "Llama 3.1 405B", "Meta", "Frontier-scale 405B parameter model for intricate problems.", "Flagship", "128k"),
        GitHubModelInfo("Meta-Llama-3.1-70B-Instruct", "Llama 3.1 70B", "Meta", "High-performance general reasoning and programming.", "Flagship", "128k"),
        GitHubModelInfo("Meta-Llama-3.1-8B-Instruct", "Llama 3.1 8B", "Meta", "Balanced, quick general purpose assistant.", "Fast", "128k"),

        // Mistral AI
        GitHubModelInfo("Mistral-large-2411", "Mistral Large", "Mistral AI", "Top-tier flagship European reasoning and multilingual model.", "Flagship", "128k"),
        GitHubModelInfo("Mistral-large-2407", "Mistral Large 2407", "Mistral AI", "Enterprise grade code generation and complex logic.", "Flagship", "128k"),
        GitHubModelInfo("Codestral-2501", "Codestral 2501", "Mistral AI", "Specialized coding powerhouse across 80+ programming languages.", "Coding", "256k"),
        GitHubModelInfo("Mistral-Nemo", "Mistral NeMo", "Mistral AI", "12B multilingual model trained with NVIDIA.", "Coding", "128k"),
        GitHubModelInfo("Mistral-small-2402", "Mistral Small", "Mistral AI", "Low-latency, highly optimized model for snappy responses.", "Fast", "32k"),
        GitHubModelInfo("Ministral-3B", "Ministral 3B", "Mistral AI", "Edge-optimized high efficiency model.", "Fast", "128k"),

        // Microsoft Phi
        GitHubModelInfo("Phi-4", "Phi-4", "Microsoft", "Compact 14B model outperforming larger models in math and logic.", "Reasoning", "16k"),
        GitHubModelInfo("Phi-3.5-MoE-instruct", "Phi-3.5 MoE", "Microsoft", "Mixture of experts architecture for multi-turn reasoning.", "Reasoning", "128k"),
        GitHubModelInfo("Phi-3.5-mini-instruct", "Phi-3.5 Mini", "Microsoft", "Fast, lightweight reasoning model.", "Fast", "128k"),
        GitHubModelInfo("Phi-3.5-vision-instruct", "Phi-3.5 Vision", "Microsoft", "Multimodal text and image understanding.", "Fast", "128k"),

        // Cohere
        GitHubModelInfo("Cohere-command-r-plus-08-2024", "Command R+", "Cohere", "Specialized for advanced tool use, RAG, and structured business logic.", "Coding", "128k"),
        GitHubModelInfo("Cohere-command-r-08-2024", "Command R", "Cohere", "Fast conversational and retrieval engine.", "Coding", "128k"),

        // Qwen
        GitHubModelInfo("Qwen2.5-Coder-32B-Instruct", "Qwen 2.5 Coder 32B", "Qwen", "World-class coding, refactoring, and code debugging specialist.", "Coding", "128k"),
        GitHubModelInfo("Qwen2.5-72B-Instruct", "Qwen 2.5 72B", "Qwen", "High-intelligence open reasoning model.", "Flagship", "128k"),

        // AI21
        GitHubModelInfo("Jamba-1.5-Large", "Jamba 1.5 Large", "AI21 Labs", "Hybrid SSM-Transformer model for massive context tasks.", "Flagship", "256k"),
        GitHubModelInfo("Jamba-1.5-Mini", "Jamba 1.5 Mini", "AI21 Labs", "Fast hybrid SSM-Transformer model.", "Fast", "256k")
    )
}
