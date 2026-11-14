# FURKAN AI

FURKAN AI is a multilingual Android personal AI assistant built with Kotlin and Jetpack Compose.

## Version 0.2.0

- Chat with the user's own API key
- Provider selection: Google Gemini, OpenAI, Grok (xAI), and OpenRouter
- AES-GCM encrypted API-key storage backed by Android Keystore
- Direct HTTPS requests from the phone to the selected AI provider
- Speech-to-text through the Android speech recognition service
- Natural and Jarvis-style robotic text-to-speech using the device TTS engine
- User-editable model ID for each provider

## API key setup

The user creates their own API key with the chosen provider, selects that provider in FURKAN AI, pastes the key into API Settings, saves it, and optionally runs a connection test.

FURKAN AI does not include a shared developer API key and does not proxy requests through a FURKAN AI server. The user's key is encrypted at rest with Android Keystore and sent directly to the selected provider over HTTPS when a request is made. API usage and billing are controlled by that provider.

Local encryption reduces exposure at rest, but it cannot guarantee protection on a compromised or modified device. Never put a shared API key in the APK or source code.

## Planned Jarvis-style milestones

1. Persistent conversation history and user-controlled memory
2. Custom assistants with names, voices, personalities, and instructions
3. Live web search with source links and deep-research reports
4. Reminders, calendar events, notes, and local file library
5. User-approved phone actions such as opening apps, calls, and message drafts
6. Image generation and camera/document understanding
7. Home-screen widget, quick actions, and device status dashboard

Some Android actions require runtime permissions or explicit user confirmation and may be restricted by the Android version or manufacturer.

## Build

Open the Actions tab and run Build FURKAN AI APK, or push a commit to main.

When the workflow succeeds, download the FURKAN-AI-debug artifact.

## Project

- Package: com.furkanai.assistant
- Minimum Android: 8.0 (API 26)
- Build JDK: 17
