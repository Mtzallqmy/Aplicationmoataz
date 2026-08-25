package ai.alaser.ai.providers

data class ProviderPreset(val name: String, val baseUrl: String)

object ProviderPresets {
    val presets = listOf(
        ProviderPreset("OpenAI", "https://api.openai.com/v1"),
        ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1"),
        ProviderPreset("Groq", "https://api.groq.com/openai/v1"),
        ProviderPreset("Together", "https://api.together.xyz/v1"),
        ProviderPreset("Fireworks", "https://api.fireworks.ai/inference/v1"),
        ProviderPreset("DeepSeek", "https://api.deepseek.com/v1"),
        ProviderPreset("Mistral", "https://api.mistral.ai/v1"),
        ProviderPreset("xAI", "https://api.x.ai/v1"),
        ProviderPreset("Cerebras", "https://api.cerebras.ai/v1"),
        ProviderPreset("NVIDIA", "https://integrate.api.nvidia.com/v1"),
    )
}
