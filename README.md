# FURKAN AI

Multilingual Android personal assistant built with Kotlin and Jetpack Compose.

## Version 0.4.0

- BYOK: Gemini 3.8 Flash, OpenAI GPT-6 Luna, Grok 4.7 and OpenRouter
- AES-GCM encrypted API keys using Android Keystore
- Manual and heuristic automatic provider routing
- Persistent local chat history, user-managed memories and notes
- Jarvis, Hacker, Teacher, Coding and custom persona profiles
- Android TTS voice selector plus Natural, Jarvis, Hacker/Deep, Robotic, Energetic and Calm presets
- Speech input, local photo/camera attachment and multimodal image understanding (model support varies)
- Provider-backed web search
- OpenAI image generation saved to Pictures/FURKAN AI
- Android Assistant role request via ACTION_ASSIST
- Local reminders, calendar event drafts, device status and installed-app launcher list
- Home-screen widget

## API keys and billing

Users supply and pay for their own provider keys. The app contains no shared developer key. Keys are encrypted with Android Keystore and sent directly to the chosen provider over HTTPS. A rooted or compromised device can still expose local secrets. Image generation and web search may incur extra provider fees.

## Android default assistant

On Android 10+, choose **Tools > Varsayılan telefon asistanı** and approve the Android system dialog. This app handles ACTION_ASSIST. It is not yet an always-listening hotword service; that requires a VoiceInteractionService plus a session implementation and depends on device support.

## Voice profiles

Presets alter pitch and speaking rate of the selected installed Android TTS voice. Available languages/voices depend on installed TTS engine packs. Hacker/Deep is a voice style preset, not a separate recorded actor voice. The Android text-to-speech settings can be used to install additional voices.

## Data and device actions

Chat history, notes, memories, and reminders are stored locally in app-private storage. Images attached to a chat are sent to the selected AI provider but are not persisted in local chat history. Calendar events open as editable system drafts; the user must save them. Phone calls open the dialer and require user confirmation. Secure system settings are not silently toggled. Reminder delivery can be delayed by battery restrictions.

## Build

Push to main or run **Build FURKAN AI APK** in GitHub Actions, then download the **FURKAN-AI-debug** artifact.
