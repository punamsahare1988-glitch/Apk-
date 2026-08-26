package com.example.server

object WebClientAssets {

    fun getIndexHtml(appName: String = "ScreenHost"): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>$appName - Local Screen Mirror & Remote Control</title>
    <style>
        :root {
            --bg-color: #080c17;
            --surface-color: #10192e;
            --surface-elevated: #182442;
            --primary: #00e5ff;
            --primary-hover: #38bdf8;
            --accent: #10b981;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --border-color: #223255;
            --danger: #ef4444;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            user-select: none;
            -webkit-user-select: none;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-main);
            height: 100vh;
            width: 100vw;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }

        /* Top Header Bar */
        header {
            height: 56px;
            background: var(--surface-color);
            border-bottom: 1px solid var(--border-color);
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0 16px;
            z-index: 10;
        }

        .brand-logo {
            display: flex;
            align-items: center;
            gap: 10px;
            font-weight: 700;
            font-size: 1.15rem;
            color: var(--primary);
            letter-spacing: 0.5px;
        }

        .brand-icon {
            width: 28px;
            height: 28px;
            border-radius: 6px;
            background: linear-gradient(135deg, #0284c7, #00e5ff);
            display: flex;
            align-items: center;
            justify-content: center;
            color: #041e2d;
            font-weight: 900;
            font-size: 16px;
        }

        .stats-badge-group {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .stat-pill {
            background: var(--surface-elevated);
            border: 1px solid var(--border-color);
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 0.75rem;
            display: flex;
            align-items: center;
            gap: 6px;
            color: var(--text-muted);
        }

        .stat-pill .val {
            color: var(--primary);
            font-weight: 600;
            font-family: monospace;
        }

        .live-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background-color: var(--accent);
            box-shadow: 0 0 8px var(--accent);
            animation: pulse 1.8s infinite;
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(0.85); }
        }

        .header-actions {
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .btn {
            background: var(--surface-elevated);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 0.82rem;
            font-weight: 600;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: all 0.2s ease;
        }

        .btn:hover {
            background: var(--primary);
            color: #041e2d;
            border-color: var(--primary);
        }

        .btn-primary {
            background: var(--primary);
            color: #041e2d;
            border-color: var(--primary);
        }

        .btn-primary:hover {
            background: var(--primary-hover);
        }

        /* Main Workspace Container */
        .workspace {
            flex: 1;
            display: flex;
            position: relative;
            overflow: hidden;
            background: radial-gradient(circle at center, #111a33 0%, #060912 100%);
        }

        /* Stream Display Area */
        .viewport-container {
            flex: 1;
            display: flex;
            align-items: center;
            justify-content: center;
            position: relative;
            padding: 12px;
            overflow: hidden;
        }

        #screenCanvas {
            max-width: 100%;
            max-height: 100%;
            object-fit: contain;
            border-radius: 12px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.7), 0 0 20px rgba(0, 229, 255, 0.15);
            background: #000;
            cursor: crosshair;
            touch-action: none;
            transition: border-radius 0.2s ease, box-shadow 0.2s ease;
        }

        /* Fullscreen Mode Overrides */
        :fullscreen header,
        :-webkit-full-screen header {
            display: none;
        }

        :fullscreen .workspace,
        :-webkit-full-screen .workspace {
            height: 100vh;
            width: 100vw;
            background: #000;
        }

        :fullscreen .viewport-container,
        :-webkit-full-screen .viewport-container {
            padding: 0;
            width: 100vw;
            height: 100vh;
        }

        :fullscreen #screenCanvas,
        :-webkit-full-screen #screenCanvas {
            max-width: 100vw;
            max-height: 100vh;
            width: 100%;
            height: 100%;
            border-radius: 0;
            box-shadow: none;
        }

        :fullscreen .floating-remote-bar,
        :-webkit-full-screen .floating-remote-bar {
            bottom: 12px;
            opacity: 0.6;
            transition: opacity 0.3s ease;
        }

        :fullscreen .floating-remote-bar:hover,
        :-webkit-full-screen .floating-remote-bar:hover {
            opacity: 1;
        }

        /* Floating Virtual Navigation Remote */
        .floating-remote-bar {
            position: absolute;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(16, 25, 46, 0.88);
            backdrop-filter: blur(12px);
            border: 1px solid var(--border-color);
            border-radius: 30px;
            padding: 6px 14px;
            display: flex;
            align-items: center;
            gap: 12px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            z-index: 5;
        }

        .remote-btn {
            background: transparent;
            border: none;
            color: var(--text-main);
            width: 38px;
            height: 38px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-size: 16px;
            transition: all 0.2s ease;
        }

        .remote-btn:hover {
            background: rgba(0, 229, 255, 0.2);
            color: var(--primary);
            transform: scale(1.1);
        }

        .remote-divider {
            width: 1px;
            height: 24px;
            background: var(--border-color);
        }

        /* Modals and Overlays */
        .modal-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(4, 8, 16, 0.85);
            backdrop-filter: blur(8px);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 100;
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.25s ease;
        }

        .modal-overlay.active {
            opacity: 1;
            pointer-events: auto;
        }

        .modal-card {
            background: var(--surface-color);
            border: 1px solid var(--border-color);
            border-radius: 16px;
            width: 90%;
            max-width: 480px;
            padding: 24px;
            box-shadow: 0 20px 50px rgba(0,0,0,0.8);
            position: relative;
        }

        .modal-title {
            font-size: 1.25rem;
            color: var(--text-main);
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .pin-input {
            width: 100%;
            padding: 14px;
            font-size: 1.5rem;
            text-align: center;
            letter-spacing: 8px;
            background: var(--bg-color);
            border: 2px solid var(--border-color);
            border-radius: 10px;
            color: var(--primary);
            font-weight: 700;
            margin: 16px 0;
            outline: none;
        }

        .pin-input:focus {
            border-color: var(--primary);
            box-shadow: 0 0 15px rgba(0, 229, 255, 0.3);
        }

        .keybind-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 12px;
            background: var(--surface-elevated);
            border-radius: 8px;
            margin-bottom: 8px;
            font-size: 0.85rem;
        }

        .key-badge {
            background: #0b1324;
            border: 1px solid var(--border-color);
            border-radius: 4px;
            padding: 3px 8px;
            font-family: monospace;
            font-weight: 700;
            color: var(--primary);
        }

        .close-modal {
            position: absolute;
            top: 16px;
            right: 16px;
            background: none;
            border: none;
            color: var(--text-muted);
            font-size: 20px;
            cursor: pointer;
        }

        .close-modal:hover {
            color: var(--danger);
        }

        /* Touch Pointer Visual Indicator */
        .touch-indicator {
            position: absolute;
            width: 24px;
            height: 24px;
            border-radius: 50%;
            background: rgba(0, 229, 255, 0.4);
            border: 2px solid var(--primary);
            pointer-events: none;
            transform: translate(-50%, -50%);
            display: none;
            box-shadow: 0 0 10px var(--primary);
            transition: width 0.1s, height 0.1s;
        }

        /* Responsive Layout */
        @media (max-width: 600px) {
            header {
                padding: 0 8px;
            }
            .stat-pill {
                display: none;
            }
            .stat-pill.essential {
                display: flex;
            }
        }
    </style>
</head>
<body>

    <!-- Header Navigation -->
    <header>
        <div class="brand-logo">
            <div class="brand-icon">⚡</div>
            <span>ScreenHost</span>
        </div>

        <div class="stats-badge-group">
            <div class="stat-pill essential">
                <div class="live-dot" id="liveDot"></div>
                <span id="statusText">Connecting</span>
            </div>
            <div class="stat-pill">
                <span>FPS:</span>
                <span class="val" id="fpsDisplay">0</span>
            </div>
            <div class="stat-pill">
                <span>Latency:</span>
                <span class="val" id="pingDisplay">0ms</span>
            </div>
            <div class="stat-pill">
                <span>Bitrate:</span>
                <span class="val" id="bitrateDisplay">0 KB/s</span>
            </div>
        </div>

        <div class="header-actions">
            <a href="/ScreenHost.apk" download="ScreenHost.apk" class="btn" title="Download Full App APK File" style="text-decoration:none;">📥 APK</a>
            <button class="btn" id="btnKeybinds" title="Keyboard Shortcuts">⌨️ Keybinds</button>
            <button class="btn" id="btnTextInject" title="Type / Paste Text">✍️ Type Text</button>
            <button class="btn" id="btnAudioToggle" title="Audio Streaming">🔊 Audio</button>
            <button class="btn btn-primary" id="btnFullscreen" title="Toggle Fullscreen">⛶ Fullscreen</button>
        </div>
    </header>

    <!-- Workspace & Screen Area -->
    <div class="workspace">
        <div class="viewport-container" id="viewportContainer">
            <canvas id="screenCanvas"></canvas>
            <div class="touch-indicator" id="touchIndicator"></div>
        </div>

        <!-- Floating Quick Navigation Bar -->
        <div class="floating-remote-bar">
            <button class="remote-btn" id="navBack" title="Back (Esc)">◀</button>
            <button class="remote-btn" id="navHome" title="Home (Win/Home)">●</button>
            <button class="remote-btn" id="navRecents" title="Recent Apps (Tab)">■</button>
            <div class="remote-divider"></div>
            <button class="remote-btn" id="navNotifications" title="Notifications (F9)">🔔</button>
            <button class="remote-btn" id="navQuickSettings" title="Quick Settings (F10)">⚙️</button>
            <button class="remote-btn" id="navVolDown" title="Volume Down (F1)">🔉</button>
            <button class="remote-btn" id="navVolUp" title="Volume Up (F2)">🔊</button>
            <button class="remote-btn" id="navScreenshot" title="Screenshot (F12)">📸</button>
            <button class="remote-btn" id="navPower" title="Power / Lock (F8)">⏻</button>
            <div class="remote-divider"></div>
            <button class="remote-btn" id="navFullscreen" title="Toggle Fullscreen (F)">⛶</button>
        </div>
    </div>

    <!-- PIN Authentication Modal -->
    <div class="modal-overlay" id="authModal">
        <div class="modal-card">
            <h2 class="modal-title">🔒 Secure Authentication</h2>
            <p style="color: var(--text-muted); font-size: 0.9rem; line-height: 1.4;">
                This phone requires a PIN passcode to grant remote viewing and control access. Check the phone screen for the current Host PIN.
            </p>
            <input type="password" id="pinCodeInput" class="pin-input" maxlength="6" placeholder="PIN" autofocus autocomplete="off" />
            <button class="btn btn-primary" id="btnSubmitPin" style="width: 100%; padding: 12px; font-size: 1rem; justify-content: center;">
                Unlock & Connect
            </button>
            <div id="authErrorMsg" style="color: var(--danger); font-size: 0.85rem; margin-top: 10px; text-align: center; display: none;">
                Invalid PIN code. Please check the phone screen.
            </div>
        </div>
    </div>

    <!-- Keybinds Configuration Modal -->
    <div class="modal-overlay" id="keybindsModal">
        <div class="modal-card">
            <button class="close-modal" id="btnCloseKeybinds">&times;</button>
            <h2 class="modal-title">⌨️ Keyboard & Mouse Controls</h2>
            <div style="max-height: 360px; overflow-y: auto; padding-right: 4px; margin-top: 12px;">
                <div class="keybind-row">
                    <span>Back Navigation</span>
                    <span class="key-badge">Escape / Right Click</span>
                </div>
                <div class="keybind-row">
                    <span>Home Screen</span>
                    <span class="key-badge">Home / Windows Key / Middle Click</span>
                </div>
                <div class="keybind-row">
                    <span>Recent Apps Switcher</span>
                    <span class="key-badge">Tab</span>
                </div>
                <div class="keybind-row">
                    <span>Volume Down / Up</span>
                    <span class="key-badge">F1 / F2</span>
                </div>
                <div class="keybind-row">
                    <span>Power / Lock Screen</span>
                    <span class="key-badge">F8</span>
                </div>
                <div class="keybind-row">
                    <span>Pull Notification Shade</span>
                    <span class="key-badge">F9</span>
                </div>
                <div class="keybind-row">
                    <span>Open Quick Settings</span>
                    <span class="key-badge">F10</span>
                </div>
                <div class="keybind-row">
                    <span>Take Screen Capture</span>
                    <span class="key-badge">F12</span>
                </div>
                <div class="keybind-row">
                    <span>Touch Tap / Click</span>
                    <span class="key-badge">Left Mouse Button</span>
                </div>
                <div class="keybind-row">
                    <span>Swipe / Scroll</span>
                    <span class="key-badge">Mouse Drag / Wheel</span>
                </div>
            </div>
            <p style="color: var(--text-muted); font-size: 0.78rem; margin-top: 12px; text-align: center;">
                Physical keystrokes are automatically mapped and injected directly to your phone.
            </p>
        </div>
    </div>

    <!-- Text Injection Modal -->
    <div class="modal-overlay" id="textModal">
        <div class="modal-card">
            <button class="close-modal" id="btnCloseText">&times;</button>
            <h2 class="modal-title">✍️ Send Text to Phone</h2>
            <p style="color: var(--text-muted); font-size: 0.85rem; margin-bottom: 12px;">
                Type or paste text below to paste or type directly on the connected phone.
            </p>
            <textarea id="injectTextInput" style="width: 100%; height: 100px; background: var(--bg-color); border: 1px solid var(--border-color); border-radius: 8px; color: var(--text-main); padding: 10px; font-size: 0.95rem; resize: none; outline: none;" placeholder="Type or paste any text or URL here..."></textarea>
            <div style="display: flex; gap: 10px; margin-top: 14px;">
                <button class="btn btn-primary" id="btnSendText" style="flex: 1; justify-content: center;">Send / Paste</button>
            </div>
        </div>
    </div>

    <script>
        (function() {
            const canvas = document.getElementById('screenCanvas');
            const ctx = canvas.getContext('2d');
            const touchIndicator = document.getElementById('touchIndicator');
            const fpsDisplay = document.getElementById('fpsDisplay');
            const pingDisplay = document.getElementById('pingDisplay');
            const bitrateDisplay = document.getElementById('bitrateDisplay');
            const statusText = document.getElementById('statusText');
            const liveDot = document.getElementById('liveDot');

            let ws = null;
            let authToken = localStorage.getItem('screenhost_auth_token') || '';
            let frameCount = 0;
            let lastFpsTime = performance.now();
            let totalBytes = 0;
            let lastBytesTime = performance.now();
            let isMouseDown = false;
            let isAudioEnabled = false;
            let audioCtx = null;
            let lastTouchX = 0, lastTouchY = 0;

            // Frame Image Loader
            const frameImg = new Image();
            frameImg.onload = function() {
                if (canvas.width !== frameImg.width || canvas.height !== frameImg.height) {
                    canvas.width = frameImg.width;
                    canvas.height = frameImg.height;
                }
                ctx.drawImage(frameImg, 0, 0);
                frameCount++;
            };

            function connectWebSocket() {
                const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
                const wsUrl = protocol + '//' + window.location.host + '/ws?token=' + encodeURIComponent(authToken);

                statusText.innerText = 'Connecting...';
                liveDot.style.backgroundColor = '#f59e0b';

                try {
                    ws = new WebSocket(wsUrl);
                    ws.binaryType = 'blob';

                    ws.onopen = function() {
                        statusText.innerText = 'Live Mirror';
                        liveDot.style.backgroundColor = '#10b981';
                        document.getElementById('authModal').classList.remove('active');
                        startPingLoop();
                    };

                    ws.onmessage = function(event) {
                        if (typeof event.data === 'string') {
                            try {
                                const msg = JSON.parse(event.data);
                                if (msg.type === 'auth_required') {
                                    document.getElementById('authModal').classList.add('active');
                                } else if (msg.type === 'auth_success') {
                                    authToken = msg.token;
                                    localStorage.setItem('screenhost_auth_token', authToken);
                                    document.getElementById('authModal').classList.remove('active');
                                } else if (msg.type === 'auth_failed') {
                                    document.getElementById('authErrorMsg').style.display = 'block';
                                } else if (msg.type === 'pong') {
                                    const rtt = performance.now() - msg.timestamp;
                                    pingDisplay.innerText = Math.round(rtt) + 'ms';
                                }
                            } catch(e) {}
                        } else if (event.data instanceof Blob) {
                            totalBytes += event.data.size;
                            const url = URL.createObjectURL(event.data);
                            frameImg.src = url;
                        }
                    };

                    ws.onclose = function() {
                        statusText.innerText = 'Disconnected';
                        liveDot.style.backgroundColor = '#ef4444';
                        setTimeout(connectWebSocket, 2000);
                    };

                    ws.onerror = function() {
                        ws.close();
                    };
                } catch (e) {
                    setTimeout(connectWebSocket, 2500);
                }
            }

            // Ping loop for Latency HUD
            function startPingLoop() {
                setInterval(() => {
                    if (ws && ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify({ type: 'ping', timestamp: performance.now() }));
                    }
                }, 1000);
            }

            // Stats calculation loop (FPS & Bitrate)
            setInterval(() => {
                const now = performance.now();
                const elapsedSec = (now - lastFpsTime) / 1000;
                if (elapsedSec > 0) {
                    const fps = Math.round(frameCount / elapsedSec);
                    fpsDisplay.innerText = fps;
                    frameCount = 0;
                    lastFpsTime = now;
                }

                const elapsedBytesSec = (now - lastBytesTime) / 1000;
                if (elapsedBytesSec > 0) {
                    const kbps = Math.round((totalBytes / 1024) / elapsedBytesSec);
                    bitrateDisplay.innerText = kbps > 1000 ? (kbps / 1000).toFixed(1) + ' MB/s' : kbps + ' KB/s';
                    totalBytes = 0;
                    lastBytesTime = now;
                }
            }, 1000);

            // Coordinate conversion from canvas to relative 0.0 - 1.0
            function getNormalizedCoords(e) {
                const rect = canvas.getBoundingClientRect();
                const x = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
                const y = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));
                return { x, y };
            }

            function sendInputEvent(payload) {
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify(payload));
                }
            }

            function sendGlobalAction(action) {
                sendInputEvent({ type: 'action', action: action });
            }

            // Canvas Mouse / Touch Event Handlers
            canvas.addEventListener('mousedown', function(e) {
                if (e.button === 0) { // Left click
                    isMouseDown = true;
                    const coords = getNormalizedCoords(e);
                    lastTouchX = coords.x;
                    lastTouchY = coords.y;
                    sendInputEvent({ type: 'down', x: coords.x, y: coords.y });
                    showTouchIndicator(e.clientX, e.clientY);
                } else if (e.button === 2) { // Right click -> Back
                    e.preventDefault();
                    sendGlobalAction('BACK');
                } else if (e.button === 1) { // Middle click -> Home
                    e.preventDefault();
                    sendGlobalAction('HOME');
                }
            });

            window.addEventListener('mousemove', function(e) {
                if (isMouseDown) {
                    const coords = getNormalizedCoords(e);
                    sendInputEvent({ type: 'move', x: coords.x, y: coords.y });
                    showTouchIndicator(e.clientX, e.clientY);
                }
            });

            window.addEventListener('mouseup', function(e) {
                if (isMouseDown && e.button === 0) {
                    isMouseDown = false;
                    const coords = getNormalizedCoords(e);
                    sendInputEvent({ type: 'up', x: coords.x, y: coords.y });
                    hideTouchIndicator();
                }
            });

            canvas.addEventListener('contextmenu', function(e) {
                e.preventDefault();
            });

            // Mouse wheel scroll simulation
            canvas.addEventListener('wheel', function(e) {
                e.preventDefault();
                const coords = getNormalizedCoords(e);
                const delta = e.deltaY > 0 ? 0.15 : -0.15;
                sendInputEvent({
                    type: 'swipe',
                    startX: coords.x,
                    startY: coords.y,
                    endX: coords.x,
                    endY: Math.max(0, Math.min(1, coords.y - delta)),
                    durationMs: 150
                });
            }, { passive: false });

            // Keyboard Shortcuts Listener
            window.addEventListener('keydown', function(e) {
                // Ignore if typing inside text input modal
                if (document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) {
                    return;
                }

                if (e.key === 'Escape' || e.key === 'Backspace') {
                    e.preventDefault();
                    sendGlobalAction('BACK');
                } else if (e.key === 'Home' || e.key === 'Meta' || e.key === 'OS') {
                    e.preventDefault();
                    sendGlobalAction('HOME');
                } else if (e.key === 'Tab') {
                    e.preventDefault();
                    sendGlobalAction('RECENTS');
                } else if (e.key === 'F1') {
                    e.preventDefault();
                    sendGlobalAction('VOLUME_DOWN');
                } else if (e.key === 'F2') {
                    e.preventDefault();
                    sendGlobalAction('VOLUME_UP');
                } else if (e.key === 'F8') {
                    e.preventDefault();
                    sendGlobalAction('POWER');
                } else if (e.key === 'F9') {
                    e.preventDefault();
                    sendGlobalAction('NOTIFICATIONS');
                } else if (e.key === 'F10') {
                    e.preventDefault();
                    sendGlobalAction('QUICK_SETTINGS');
                } else if (e.key === 'F11') {
                    e.preventDefault();
                    toggleFullscreen();
                } else if (e.key === 'F12') {
                    e.preventDefault();
                    sendGlobalAction('SCREENSHOT');
                } else if (e.key.length === 1 && !e.ctrlKey && !e.altKey && !e.metaKey) {
                    sendInputEvent({ type: 'text', text: e.key });
                }
            });

            function showTouchIndicator(x, y) {
                touchIndicator.style.left = x + 'px';
                touchIndicator.style.top = y + 'px';
                touchIndicator.style.display = 'block';
            }

            function hideTouchIndicator() {
                touchIndicator.style.display = 'none';
            }

            // Remote Navigation Button Actions
            document.getElementById('navBack').onclick = () => sendGlobalAction('BACK');
            document.getElementById('navHome').onclick = () => sendGlobalAction('HOME');
            document.getElementById('navRecents').onclick = () => sendGlobalAction('RECENTS');
            document.getElementById('navNotifications').onclick = () => sendGlobalAction('NOTIFICATIONS');
            document.getElementById('navQuickSettings').onclick = () => sendGlobalAction('QUICK_SETTINGS');
            document.getElementById('navVolDown').onclick = () => sendGlobalAction('VOLUME_DOWN');
            document.getElementById('navVolUp').onclick = () => sendGlobalAction('VOLUME_UP');
            document.getElementById('navScreenshot').onclick = () => sendGlobalAction('SCREENSHOT');
            document.getElementById('navPower').onclick = () => sendGlobalAction('POWER');

            // PIN Authentication Submission
            document.getElementById('btnSubmitPin').onclick = function() {
                const pin = document.getElementById('pinCodeInput').value.trim();
                if (pin.length > 0 && ws && ws.readyState === WebSocket.OPEN) {
                    document.getElementById('authErrorMsg').style.display = 'none';
                    ws.send(JSON.stringify({ type: 'auth', pin: pin }));
                }
            };
            document.getElementById('pinCodeInput').addEventListener('keydown', function(e) {
                if (e.key === 'Enter') document.getElementById('btnSubmitPin').click();
            });

            // Modals Controls
            document.getElementById('btnKeybinds').onclick = () => document.getElementById('keybindsModal').classList.add('active');
            document.getElementById('btnCloseKeybinds').onclick = () => document.getElementById('keybindsModal').classList.remove('active');

            document.getElementById('btnTextInject').onclick = () => {
                document.getElementById('textModal').classList.add('active');
                document.getElementById('injectTextInput').focus();
            };
            document.getElementById('btnCloseText').onclick = () => document.getElementById('textModal').classList.remove('active');
            document.getElementById('btnSendText').onclick = () => {
                const text = document.getElementById('injectTextInput').value;
                if (text.length > 0) {
                    sendInputEvent({ type: 'text', text: text });
                    document.getElementById('injectTextInput').value = '';
                    document.getElementById('textModal').classList.remove('active');
                }
            };

            // Enhanced Fullscreen Controller
            function isFullscreen() {
                return !!(document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement);
            }

            function toggleFullscreen() {
                const elem = document.documentElement;
                if (!isFullscreen()) {
                    if (elem.requestFullscreen) {
                        elem.requestFullscreen().catch(() => {});
                    } else if (elem.webkitRequestFullscreen) {
                        elem.webkitRequestFullscreen();
                    } else if (elem.mozRequestFullScreen) {
                        elem.mozRequestFullScreen();
                    } else if (elem.msRequestFullscreen) {
                        elem.msRequestFullscreen();
                    }
                } else {
                    if (document.exitFullscreen) {
                        document.exitFullscreen().catch(() => {});
                    } else if (document.webkitExitFullscreen) {
                        document.webkitExitFullscreen();
                    } else if (document.mozCancelFullScreen) {
                        document.mozCancelFullScreen();
                    } else if (document.msExitFullscreen) {
                        document.msExitFullscreen();
                    }
                }
            }

            function updateFullscreenUI() {
                const active = isFullscreen();
                const btnFs = document.getElementById('btnFullscreen');
                const navFs = document.getElementById('navFullscreen');
                if (btnFs) btnFs.innerHTML = active ? '🗗 Exit Fullscreen' : '⛶ Fullscreen';
                if (navFs) navFs.innerHTML = active ? '🗗' : '⛶';
            }

            document.getElementById('btnFullscreen').onclick = toggleFullscreen;
            const navFsBtn = document.getElementById('navFullscreen');
            if (navFsBtn) navFsBtn.onclick = toggleFullscreen;

            // Double click canvas to toggle fullscreen
            canvas.addEventListener('dblclick', function(e) {
                e.preventDefault();
                toggleFullscreen();
            });

            // Listen to browser fullscreen change events
            ['fullscreenchange', 'webkitfullscreenchange', 'mozfullscreenchange', 'MSFullscreenChange'].forEach(event => {
                document.addEventListener(event, updateFullscreenUI);
            });

            // Audio Toggle
            document.getElementById('btnAudioToggle').onclick = () => {
                isAudioEnabled = !isAudioEnabled;
                document.getElementById('btnAudioToggle').style.color = isAudioEnabled ? 'var(--primary)' : 'var(--text-main)';
                if (isAudioEnabled) {
                    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                    if (audioCtx.state === 'suspended') audioCtx.resume();
                }
            };

            // Start Initial Connection
            connectWebSocket();
        })();
    </script>
</body>
</html>
        """.trimIndent()
    }

    fun getDownloadPageHtml(appName: String = "ScreenHost", hostUrl: String, downloadCode: String): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Download & Connect - $appName</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #080c17;
            color: #f8fafc;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 20px;
            margin: 0;
        }
        .card {
            background: #10192e;
            border: 1px solid #223255;
            border-radius: 16px;
            max-width: 500px;
            width: 100%;
            padding: 30px;
            box-shadow: 0 20px 40px rgba(0,0,0,0.6);
            text-align: center;
        }
        .logo-icon {
            width: 56px;
            height: 56px;
            border-radius: 14px;
            background: linear-gradient(135deg, #0284c7, #00e5ff);
            margin: 0 auto 16px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 28px;
            color: #041e2d;
            font-weight: bold;
        }
        h1 { font-size: 1.5rem; margin-bottom: 8px; color: #00e5ff; }
        p { color: #94a3b8; font-size: 0.95rem; line-height: 1.5; margin-bottom: 20px; }
        .code-box {
            background: #060912;
            border: 2px dashed #00e5ff;
            border-radius: 10px;
            padding: 14px;
            font-family: monospace;
            font-size: 1.8rem;
            font-weight: 700;
            color: #00e5ff;
            letter-spacing: 6px;
            margin: 16px 0 24px;
        }
        .btn {
            display: block;
            width: 100%;
            background: #00e5ff;
            color: #041e2d;
            padding: 14px;
            border-radius: 8px;
            font-weight: 700;
            text-decoration: none;
            font-size: 1rem;
            box-sizing: border-box;
            transition: opacity 0.2s;
        }
        .btn:hover { opacity: 0.9; }
    </style>
</head>
<body>
    <div class="card">
        <div class="logo-icon">⚡</div>
        <h1>$appName Direct Host</h1>
        <p>You are connected directly to the local host server over Wi-Fi.</p>
        <div>Quick Pairing Download Code:</div>
        <div class="code-box">$downloadCode</div>
        <div style="display:flex; flex-direction:column; gap:12px;">
            <a href="$hostUrl" class="btn">Launch Screen Viewer Portal</a>
            <a href="/ScreenHost.apk" download="ScreenHost.apk" class="btn" style="background:#1e293b; color:#38bdf8; border:1px solid #38bdf8;">📥 Download App APK File (.apk)</a>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
}
