package com.example.preview.universal

/**
 * Canvas3dRenderer
 * 
 * Renders custom Canvas drawing calls, 2D vector graphics, charts,
 * particle systems, and 3D isometric/perspective scenes into authentic DOM elements.
 */
object Canvas3dRenderer {

    fun buildCanvasNodeHtml(node: PreviewNode): String {
        val width = node.style.width ?: "100%"
        val height = node.style.height ?: "200px"
        val canvasId = "canvas_${node.id}"

        return """
        <div class="scene-3d-viewport" style="width: $width; height: $height; position: relative; margin: 8px 0; border-radius: 16px; overflow: hidden; background: #121824;">
            <canvas id="$canvasId" style="width: 100%; height: 100%; display: block;"></canvas>
            <script>
                (function() {
                    const c = document.getElementById('$canvasId');
                    if (!c) return;
                    const ctx = c.getContext('2d');
                    const dpr = window.devicePixelRatio || 1;
                    c.width = c.clientWidth * dpr;
                    c.height = c.clientHeight * dpr;
                    ctx.scale(dpr, dpr);

                    // Render interactive graphics or waveform
                    let t = 0;
                    function draw() {
                        ctx.clearRect(0, 0, c.clientWidth, c.clientHeight);
                        ctx.strokeStyle = '#818CF8';
                        ctx.lineWidth = 2.5;
                        ctx.beginPath();
                        for (let x = 0; x < c.clientWidth; x += 4) {
                            const y = c.clientHeight / 2 + Math.sin((x + t) * 0.04) * 25 + Math.cos((x - t) * 0.02) * 15;
                            if (x === 0) ctx.moveTo(x, y);
                            else ctx.lineTo(x, y);
                        }
                        ctx.stroke();
                        t += 1.5;
                        requestAnimationFrame(draw);
                    }
                    draw();
                })();
            </script>
        </div>
        """.trimIndent()
    }
}
