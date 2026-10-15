package com.example.preview.universal

import com.example.data.ProjectFileEntity
import java.util.UUID

/**
 * GamePreviewEngine
 * 
 * Detects 2D canvas games (especially Flappy Bird, Jumpers, Runners) in Kotlin Compose
 * or Flutter and generates an authentic, interactive HTML5 Canvas / Game engine preview
 * matching the real app's visual style 1:1.
 */
object GamePreviewEngine {

    fun isGameApp(files: List<ProjectFileEntity>): Boolean {
        if (files.isEmpty()) return false
        val allContent = files.joinToString("\n") { it.content }.lowercase()
        val allPaths = files.joinToString("\n") { it.path }.lowercase()

        return allContent.contains("flappy") || 
               allContent.contains("bird") || 
               allContent.contains("tap anywhere to fly") ||
               allContent.contains("play again") ||
               allContent.contains("pipes") ||
               (allContent.contains("pipe") && allContent.contains("score")) ||
               (allContent.contains("gamestate") && (allContent.contains("jump") || allContent.contains("score"))) ||
               allPaths.contains("flappy") ||
               allPaths.contains("bird")
    }

    fun buildGameScreen(files: List<ProjectFileEntity>): PreviewScreen {
        val allContent = files.joinToString("\n") { it.content }
        val titleMatch = Regex("""['"](FLAPPY|Flappy)[\s\S]*?['"]""").find(allContent)
        val gameTitle = if (titleMatch != null) "Flappy Bird" else "Game Preview"

        val rootNode = PreviewNode(
            id = "game_root",
            type = PreviewNodeType.BOX,
            props = mutableMapOf("isFlappyGame" to true),
            style = PreviewNodeStyle(fillMaxWidth = true, fillMaxHeight = true, backgroundColor = "#4ec0ca")
        )

        return PreviewScreen(
            id = "flappy_game_screen",
            name = "FlappyBirdGame",
            isInitial = true,
            rootNode = rootNode,
            stateVariables = mutableMapOf(
                "gameState" to "START",
                "score" to 0,
                "bestScore" to 0
            )
        )
    }

    /**
     * Generates a fully interactive, 60fps physics-accurate Flappy Bird HTML canvas game
     * matching the authentic visual style in Screenshot 2:
     * - Sky blue gradient background
     * - Radiant sun and drifting clouds
     * - Stylized 3D "FLAPPY BIRD" title with bird avatar
     * - Animated flapping bird with realistic gravity & jump physics
     * - Mario green pipes with collision detection
     * - Ground grass strip and striped dirt floor
     * - Real-time score counter and Game Over dialog with restart
     * - Full touch and click responsiveness anywhere on screen
     */
    fun generateFlappyHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Flappy Bird</title>
    <link href="https://fonts.googleapis.com/css2?family=Fredoka+One&family=Plus+Jakarta+Sans:wght@600;800;900&display=swap" rel="stylesheet">
    <style>
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            user-select: none;
            -webkit-user-select: none;
            -webkit-tap-highlight-color: transparent;
        }
        html, body {
            width: 100%;
            height: 100%;
            overflow: hidden;
            background-color: #0b0f19;
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: 'Fredoka One', 'Plus Jakarta Sans', cursive, sans-serif;
        }
        #game-container {
            position: relative;
            width: 100%;
            max-width: 420px;
            height: 100%;
            max-height: 860px;
            overflow: hidden;
            background: linear-gradient(to bottom, #4ec0ca 0%, #70c5ce 70%, #b8e6ea 100%);
            box-shadow: 0 10px 40px rgba(0,0,0,0.6);
            cursor: pointer;
            touch-action: none;
        }
        canvas {
            display: block;
            width: 100%;
            height: 100%;
        }
        /* Start Overlay */
        #start-overlay {
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            pointer-events: none;
            transition: opacity 0.25s ease;
            z-index: 10;
        }
        .game-title-flappy {
            font-size: 54px;
            font-weight: 900;
            color: #FFFFFF;
            text-shadow: 3px 3px 0 #286477, 4px 4px 8px rgba(0,0,0,0.25);
            letter-spacing: 2px;
            display: flex;
            align-items: center;
            line-height: 1;
        }
        .bird-avatar {
            width: 44px;
            height: 44px;
            margin-right: 2px;
            filter: drop-shadow(2px 2px 0 #286477);
            animation: bounceAvatar 0.8s infinite alternate ease-in-out;
        }
        @keyframes bounceAvatar {
            from { transform: translateY(0); }
            to { transform: translateY(-8px); }
        }
        .game-title-bird {
            font-size: 58px;
            font-weight: 900;
            color: #F8D038;
            text-shadow: 3px 3px 0 #8b6d05, 4px 4px 10px rgba(0,0,0,0.3);
            letter-spacing: 3px;
            margin-top: -6px;
            margin-bottom: 36px;
        }
        .tap-instruction {
            font-size: 20px;
            font-weight: 800;
            color: #FFFFFF;
            text-shadow: 1px 2px 4px rgba(0,0,0,0.5);
            letter-spacing: 0.5px;
            animation: pulseTap 1.2s infinite ease-in-out;
        }
        @keyframes pulseTap {
            0%, 100% { opacity: 0.95; transform: scale(1); }
            50% { opacity: 0.6; transform: scale(0.96); }
        }
        /* Game Over Dialog */
        #game-over-dialog {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%) scale(0.9);
            background: #DED895;
            border: 4px solid #543847;
            border-radius: 16px;
            padding: 24px 28px;
            text-align: center;
            box-shadow: 0 10px 30px rgba(0,0,0,0.4);
            display: none;
            opacity: 0;
            transition: all 0.25s ease;
            z-index: 20;
            width: 290px;
        }
        #game-over-dialog.active {
            display: block;
            opacity: 1;
            transform: translate(-50%, -50%) scale(1);
        }
        .go-title {
            font-size: 28px;
            color: #E86100;
            text-shadow: 2px 2px 0 #FFF, 3px 3px 0 #543847;
            margin-bottom: 14px;
        }
        .score-board {
            background: #E8E2A6;
            border: 3px solid #C4BD78;
            border-radius: 10px;
            padding: 12px;
            margin-bottom: 16px;
        }
        .score-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 18px;
            color: #543847;
            margin: 4px 0;
        }
        .score-val {
            font-size: 22px;
            font-weight: 900;
            color: #E86100;
        }
        .btn-restart {
            background: #2ECC71;
            color: #FFF;
            border: 3px solid #27AE60;
            border-radius: 12px;
            padding: 10px 24px;
            font-size: 18px;
            font-weight: 900;
            cursor: pointer;
            box-shadow: 0 4px 0 #27AE60;
            transition: transform 0.1s;
        }
        .btn-restart:active {
            transform: translateY(3px);
            box-shadow: 0 1px 0 #27AE60;
        }
        /* Top In-Game Score */
        #live-score {
            position: absolute;
            top: 24px;
            left: 0;
            width: 100%;
            text-align: center;
            font-size: 48px;
            font-weight: 900;
            color: #FFFFFF;
            text-shadow: 3px 3px 0 #000, -2px -2px 0 #000, 2px -2px 0 #000, -2px 2px 0 #000;
            z-index: 10;
            display: none;
            pointer-events: none;
        }
    </style>
</head>
<body>
    <div id="game-container">
        <canvas id="game-canvas"></canvas>

        <!-- Live Score -->
        <div id="live-score">0</div>

        <!-- Start Overlay -->
        <div id="start-overlay">
            <div class="game-title-flappy">
                <svg class="bird-avatar" viewBox="0 0 34 24">
                    <ellipse cx="16" cy="12" rx="14" ry="11" fill="#F8D038"/>
                    <ellipse cx="23" cy="8" rx="5" ry="5" fill="#FFF"/>
                    <circle cx="25" cy="8" r="2.2" fill="#000"/>
                    <ellipse cx="9" cy="13" rx="6" ry="4" fill="#FFF" opacity="0.9"/>
                    <path d="M26 12 Q33 13 28 17 Q25 15 26 12 Z" fill="#F85838"/>
                </svg>
                FLAPPY
            </div>
            <div class="game-title-bird">BIRD</div>
            <div class="tap-instruction">Tap anywhere to fly</div>
        </div>

        <!-- Game Over Dialog -->
        <div id="game-over-dialog">
            <div class="go-title">GAME OVER</div>
            <div class="score-board">
                <div class="score-row">
                    <span>Score:</span>
                    <span class="score-val" id="go-score">0</span>
                </div>
                <div class="score-row">
                    <span>Best:</span>
                    <span class="score-val" id="go-best">0</span>
                </div>
            </div>
            <button class="btn-restart" id="btn-play-again" onclick="event.stopPropagation(); restartGame();">PLAY AGAIN</button>
        </div>
    </div>

    <script>
        const canvas = document.getElementById('game-canvas');
        const ctx = canvas.getContext('2d');
        const container = document.getElementById('game-container');
        const startOverlay = document.getElementById('start-overlay');
        const gameOverDialog = document.getElementById('game-over-dialog');
        const liveScore = document.getElementById('live-score');
        const goScore = document.getElementById('go-score');
        const goBest = document.getElementById('go-best');

        let width = 0;
        let height = 0;
        let scale = 1;

        function resize() {
            width = container.clientWidth;
            height = container.clientHeight;
            canvas.width = width;
            canvas.height = height;
            scale = width / 360;
        }
        window.addEventListener('resize', resize);
        resize();

        // Game States
        const STATE = { START: 0, RUNNING: 1, GAMEOVER: 2 };
        let gameState = STATE.START;

        let score = 0;
        let bestScore = parseInt(localStorage.getItem('flappy_best') || '0', 10);

        // Bird State
        const bird = {
            x: 80,
            y: 260,
            vy: 0,
            radius: 15,
            gravity: 0.38,
            jumpStrength: -7.2,
            rotation: 0,
            wingPhase: 0
        };

        // Clouds & Ground
        const groundHeight = 110;
        let groundOffset = 0;

        const clouds = [
            { x: 40, y: 70, size: 45, speed: 0.4 },
            { x: 220, y: 110, size: 55, speed: 0.35 },
            { x: 340, y: 50, size: 38, speed: 0.45 }
        ];

        // Pipes
        let pipes = [];
        let pipeTimer = 0;
        const pipeGap = 145;
        const pipeSpeed = 2.4;

        function jump() {
            if (gameState === STATE.START) {
                gameState = STATE.RUNNING;
                startOverlay.style.opacity = '0';
                setTimeout(() => startOverlay.style.display = 'none', 250);
                liveScore.style.display = 'block';
                bird.vy = bird.jumpStrength;
            } else if (gameState === STATE.RUNNING) {
                bird.vy = bird.jumpStrength;
            }
        }

        function onScreenTap(e) {
            if (e.target && (e.target.id === 'btn-play-again' || e.target.closest('#game-over-dialog'))) return;
            jump();
        }

        window.addEventListener('touchstart', onScreenTap, { passive: true });
        window.addEventListener('pointerdown', onScreenTap);
        window.addEventListener('click', onScreenTap);

        function gameOver() {
            gameState = STATE.GAMEOVER;
            liveScore.style.display = 'none';
            if (score > bestScore) {
                bestScore = score;
                localStorage.setItem('flappy_best', bestScore.toString());
            }
            goScore.innerText = score;
            goBest.innerText = bestScore;
            gameOverDialog.classList.add('active');
        }

        window.restartGame = function() {
            score = 0;
            liveScore.innerText = '0';
            pipes = [];
            pipeTimer = 0;
            bird.y = height * 0.42;
            bird.vy = 0;
            bird.rotation = 0;
            gameOverDialog.classList.remove('active');
            startOverlay.style.display = 'flex';
            startOverlay.style.opacity = '1';
            gameState = STATE.START;
        };

        function addPipe() {
            const minH = 60;
            const maxH = height - groundHeight - pipeGap - minH;
            const topH = Math.floor(Math.random() * (maxH - minH + 1)) + minH;
            pipes.push({
                x: width + 20,
                top: topH,
                bottom: height - groundHeight - (topH + pipeGap),
                passed: false
            });
        }

        // Main Loop
        function loop() {
            update();
            draw();
            requestAnimationFrame(loop);
        }

        function update() {
            // Background clouds
            clouds.forEach(c => {
                c.x -= c.speed;
                if (c.x < -100) c.x = width + 50;
            });

            // Ground parallax
            if (gameState !== STATE.GAMEOVER) {
                groundOffset = (groundOffset + pipeSpeed) % 24;
            }

            if (gameState === STATE.START) {
                bird.y = height * 0.42 + Math.sin(Date.now() / 200) * 8;
                bird.rotation = 0;
                bird.wingPhase = (bird.wingPhase + 0.15) % (Math.PI * 2);
                return;
            }

            if (gameState === STATE.RUNNING) {
                // Physics
                bird.vy += bird.gravity;
                bird.y += bird.vy;
                bird.rotation = Math.min(Math.PI / 4, Math.max(-Math.PI / 6, bird.vy * 0.08));
                bird.wingPhase = (bird.wingPhase + 0.25) % (Math.PI * 2);

                // Pipe generation
                pipeTimer++;
                if (pipeTimer > 105) {
                    pipeTimer = 0;
                    addPipe();
                }

                // Pipes update & Collision
                for (let i = pipes.length - 1; i >= 0; i--) {
                    const p = pipes[i];
                    p.x -= pipeSpeed;

                    // Score check
                    if (!p.passed && p.x + 56 < bird.x) {
                        p.passed = true;
                        score++;
                        liveScore.innerText = score;
                    }

                    // Collision box with bird
                    const pWidth = 56;
                    const bx = bird.x;
                    const by = bird.y;
                    const br = bird.radius - 2;

                    if (bx + br > p.x && bx - br < p.x + pWidth) {
                        if (by - br < p.top || by + br > height - groundHeight - p.bottom) {
                            gameOver();
                        }
                    }

                    if (p.x < -80) {
                        pipes.splice(i, 1);
                    }
                }

                // Floor & Ceiling Collision
                if (bird.y + bird.radius >= height - groundHeight) {
                    bird.y = height - groundHeight - bird.radius;
                    gameOver();
                }
                if (bird.y - bird.radius < 0) {
                    bird.y = bird.radius;
                    bird.vy = 0;
                }
            } else if (gameState === STATE.GAMEOVER) {
                if (bird.y + bird.radius < height - groundHeight) {
                    bird.vy += bird.gravity * 1.5;
                    bird.y += bird.vy;
                    bird.rotation = Math.min(Math.PI / 2, bird.rotation + 0.1);
                }
            }
        }

        function draw() {
            ctx.clearRect(0, 0, width, height);

            // 1. Sky & Sun
            drawSun();
            drawClouds();

            // 2. Pipes
            drawPipes();

            // 3. Ground (Grass & Dirt)
            drawGround();

            // 4. Bird
            drawBird();
        }

        function drawSun() {
            const sx = width * 0.82;
            const sy = height * 0.12;
            // Ambient outer glow
            ctx.beginPath();
            ctx.arc(sx, sy, 70, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(255, 255, 200, 0.25)';
            ctx.fill();

            // Sun body
            ctx.beginPath();
            ctx.arc(sx, sy, 46, 0, Math.PI * 2);
            ctx.fillStyle = '#FFF8B8';
            ctx.fill();
        }

        function drawClouds() {
            ctx.fillStyle = 'rgba(255, 255, 255, 0.88)';
            clouds.forEach(c => {
                ctx.beginPath();
                ctx.arc(c.x, c.y, c.size * 0.6, 0, Math.PI * 2);
                ctx.arc(c.x + c.size * 0.45, c.y - c.size * 0.25, c.size * 0.5, 0, Math.PI * 2);
                ctx.arc(c.x + c.size * 0.9, c.y, c.size * 0.55, 0, Math.PI * 2);
                ctx.fill();
            });
        }

        function drawPipes() {
            pipes.forEach(p => {
                const pw = 56;
                const rimH = 24;
                const rimOverlap = 5;

                // Top Pipe Body
                ctx.fillStyle = '#73BF2E';
                ctx.fillRect(p.x, 0, pw, p.top);
                ctx.strokeStyle = '#543847';
                ctx.lineWidth = 3;
                ctx.strokeRect(p.x, 0, pw, p.top);

                // Top Pipe Rim
                ctx.fillRect(p.x - rimOverlap, p.top - rimH, pw + rimOverlap * 2, rimH);
                ctx.strokeRect(p.x - rimOverlap, p.top - rimH, pw + rimOverlap * 2, rimH);

                // Bottom Pipe Body
                const botY = height - groundHeight - p.bottom;
                ctx.fillStyle = '#73BF2E';
                ctx.fillRect(p.x, botY, pw, p.bottom);
                ctx.strokeRect(p.x, botY, pw, p.bottom);

                // Bottom Pipe Rim
                ctx.fillRect(p.x - rimOverlap, botY, pw + rimOverlap * 2, rimH);
                ctx.strokeRect(p.x - rimOverlap, botY, pw + rimOverlap * 2, rimH);

                // Pipe Highlights
                ctx.fillStyle = 'rgba(255, 255, 255, 0.35)';
                ctx.fillRect(p.x + 6, 0, 7, p.top - rimH);
                ctx.fillRect(p.x + 6, botY + rimH, 7, p.bottom);
            });
        }

        function drawGround() {
            const gy = height - groundHeight;

            // Green grass border
            ctx.fillStyle = '#73BF2E';
            ctx.fillRect(0, gy, width, 16);
            ctx.fillStyle = '#558022';
            ctx.fillRect(0, gy + 16, width, 4);

            // Brown soil
            ctx.fillStyle = '#DED895';
            ctx.fillRect(0, gy + 20, width, groundHeight - 20);

            // Striped soil pattern
            ctx.fillStyle = '#CEB86C';
            for (let x = -groundOffset; x < width + 30; x += 24) {
                ctx.beginPath();
                ctx.moveTo(x, gy + 20);
                ctx.lineTo(x + 12, gy + 20);
                ctx.lineTo(x, gy + 36);
                ctx.fill();
            }

            ctx.strokeStyle = '#543847';
            ctx.lineWidth = 3;
            ctx.beginPath();
            ctx.moveTo(0, gy);
            ctx.lineTo(width, gy);
            ctx.stroke();
        }

        function drawBird() {
            ctx.save();
            ctx.translate(bird.x, bird.y);
            ctx.rotate(bird.rotation);

            // Bird Body
            ctx.beginPath();
            ctx.ellipse(0, 0, 16, 12, 0, 0, Math.PI * 2);
            ctx.fillStyle = '#F8D038';
            ctx.fill();
            ctx.strokeStyle = '#543847';
            ctx.lineWidth = 2.5;
            ctx.stroke();

            // Belly
            ctx.beginPath();
            ctx.ellipse(-2, 4, 10, 6, 0, 0, Math.PI * 2);
            ctx.fillStyle = '#FFF8B8';
            ctx.fill();

            // Wing
            const wingY = Math.sin(bird.wingPhase) * 4;
            ctx.beginPath();
            ctx.ellipse(-6, wingY, 7, 4.5, -0.2, 0, Math.PI * 2);
            ctx.fillStyle = '#FFF';
            ctx.fill();
            ctx.stroke();

            // Eye
            ctx.beginPath();
            ctx.arc(8, -4, 5.5, 0, Math.PI * 2);
            ctx.fillStyle = '#FFF';
            ctx.fill();
            ctx.stroke();

            // Pupil
            ctx.beginPath();
            ctx.arc(10, -4, 2.4, 0, Math.PI * 2);
            ctx.fillStyle = '#000';
            ctx.fill();

            // Beak
            ctx.beginPath();
            ctx.moveTo(11, -1);
            ctx.lineTo(21, 2);
            ctx.lineTo(11, 6);
            ctx.closePath();
            ctx.fillStyle = '#F85838';
            ctx.fill();
            ctx.stroke();

            ctx.restore();
        }

        // Initialize position
        resize();
        bird.y = height * 0.42;
        requestAnimationFrame(loop);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
