# FURKAN AI

A multilingual Android assistant built with Kotlin and Jetpack Compose.

## Version 0.3.0
- BYOK support for Google Gemini, OpenAI, Grok (xAI) and OpenRouter
- API keys encrypted locally using Android Keystore and AES-GCM
- Manual and automatic provider selection
- Chat history and user-controlled memories stored locally
- Built-in and custom assistant personalities: Jarvis, Hacker, Teacher and Coding
- Android text-to-speech voices plus Natural, Jarvis, Hacker/Deep, Robotic, Energetic and Calm presets
- Speech recognition input
- Provider-backed web search and research-style responses
- Android assistant role eligibility using ACTION_ASSIST
- Local reminders, notifications, device status and launcher app shortcuts

## API keys
Users supply and pay for their own provider API keys. No shared developer key is included. Keys are encrypted at rest with Android Keystore and sent directly to the provider over HTTPS. A compromised or rooted device may still expose secrets.

## Android default assistant
On Android 10+, choose Tools > Make default assistant. Android will display its own role confirmation. This app handles ACTION_ASSIST; it does not implement an always-listening wake word or a full VoiceInteractionService session. Supported invocation gestures vary by device and OS version.

## Voice profiles
Presets change pitch and speaking rate of the chosen installed Android TTS voice. Available voices depend on installed TTS engine packs. Hacker/Deep is a style preset, not a separate recorded voice. For richer voices, install a compatible voice pack via Android text-to-speech settings.

## Web search and cost
Web search uses the chosen provider's own search tool and can add provider charges. Deep research prompts automatically request search where the provider supports it. OpenRouter uses its web-search server tool.

## Device tools and reminders
App opening is user-initiated. Device status displays battery, RAM and app data-partition storage. Reminders use Android's inexact alarm scheduling and notification permission; battery optimization may delay delivery. Secure system settings are not changed silently.

## Build
Push to main or run the GitHub Actions workflow. Download the FURKAN-AI-debug artifact after a successful run.
