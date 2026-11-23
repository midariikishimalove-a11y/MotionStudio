# Inventário de funcionalidades e status

Fonte da análise: nomes de arquivos/bibliotecas do APK de referência e strings de `resources.arsc` (análise estática de
metadados). **Nenhum código, asset, modelo ou mecanismo de licença do app de referência foi usado.**
Legenda — Evidência: **C** = confirmada por strings/arquivos; **I** = inferida (não encontrada literalmente).
Status no Motion Studio: ✅ implementado (código escrito, **ainda não compilado/testado**) · ❌ não implementado.

| Funcionalidade | Evidência | Status |
|---|---|---|
| Dividir, cortar (trim), excluir, duplicar, substituir clip | C | ✅ dividir/excluir · ❌ duplicar/substituir |
| Timeline multicamadas / sobreposições (overlay) | C | ✅ (sem deslocamento temporal de overlay) |
| Velocidade constante | C | ✅ |
| Curvas de velocidade | I | ❌ |
| Slow-mo suave / optical flow | C | ❌ |
| Vídeo reverso, congelar quadro | C | ❌ |
| Volume, extrair áudio, fade in/out | C | ✅ volume · ❌ extrair áudio, fades |
| Equalizador, separar voz, redução de ruído de áudio | C (Remove vocal, Reduce noise) / I (EQ) | ❌ |
| Brilho, contraste, saturação (HSL), preto e branco | C | ✅ |
| Curvas, roda de cores, temperatura, vinheta, nitidez, sombras/realces | C | ❌ |
| LUT (.cube) | I (nenhuma string "LUT") | ✅ |
| Chroma key / tela verde | C | ✅ (shader próprio, cor fixa verde) |
| Máscaras | C | ❌ |
| Keyframes | C | ❌ (modelo ainda não tem) |
| Modos de mesclagem (Blend, Multiply…) | C | ❌ |
| Glitch, partículas, distorção, desfoque, motion blur | C | ❌ |
| Zoom 3D / transformações | C | ❌ |
| Transições | C | ❌ |
| Estabilização | C | ❌ |
| Rastreamento (camera tracking) | C | ❌ |
| Legendas automáticas, transcrição, tradução, letras | C | ❌ (plano: Whisper local) |
| Texto, estilos, animação de texto, presets, adesivos | C | ❌ |
| Remover fundo / recorte (cutout), isolar pessoa | C | ❌ (plano: modelo de segmentação local) |
| Síntese de voz (TTS), clonagem de voz, voice changer | C | ❌ |
| Proporções 16:9, 9:16, 1:1, 4:3 | C | ✅ |
| Desfazer/refazer, rascunhos, salvamento automático | C (Undo/Redo/Draft) | ✅ |
| Exportação 720p–4K, 30/60 fps, H.264/H.265, bitrate | C (2K, 4K, 1080p, Frame rate, Resolution) | ✅ via Media3 Transformer / MediaCodec (bitrate: parâmetro existe, sem UI) |
| Teleprompter, gravação de câmera/voz | C | ❌ |
| Templates, split-screen, smart crop, auto reframe, retoque facial, HDR inteligente | C | ❌ |
| Contas, anúncios, billing, créditos, sync na nuvem | C | Fora do escopo (não replicado) |

## Limitações conhecidas da fase 1
- Clips de sobreposição começam em t=0 (sem gaps/offset); imagens não são suportadas como mídia.
- Pré-visualização usa `CompositionPlayer` (API experimental do Media3); exportação em `Android/data/studio.motion/files/Movies`.
- Preview e export usam a mesma `Composition`, então o que se vê é o que se exporta.
