#!/bin/bash
RES_DIR="app/src/main/res"

# Clean all legacy raster icons to avoid corruption
rm -f $RES_DIR/mipmap-*/ic_launcher.png
rm -f $RES_DIR/mipmap-*/ic_launcher_round.png
rm -f $RES_DIR/mipmap-*/ic_launcher.webp
rm -f $RES_DIR/mipmap-*/ic_launcher_round.webp

# Create the adaptive icon foreground (Vector)
cat << 'XML' > "$RES_DIR/drawable/ic_launcher_foreground.xml"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Left Bracket (Yellow/Gold Glowing) -->
    <path
        android:strokeWidth="10"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 38,34 L 18,54 L 38,74">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:startX="18"
                android:startY="34"
                android:endX="38"
                android:endY="74"
                android:type="linear">
                <item android:color="#FFF176" android:offset="0.0"/>
                <item android:color="#FFB300" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>

    <!-- Right Bracket (Green Glowing) -->
    <path
        android:strokeWidth="10"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 70,34 L 90,54 L 70,74">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:startX="70"
                android:startY="34"
                android:endX="90"
                android:endY="74"
                android:type="linear">
                <item android:color="#69F0AE" android:offset="0.0"/>
                <item android:color="#00E676" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>

    <!-- Pen Body (Glossy Black/Dark Grey with highlights) -->
    <path
        android:fillColor="#111111"
        android:pathData="M 49,38 L 59,38 L 59,78 C 59,85 54,88 54,88 C 54,88 49,85 49,78 Z" />
    
    <path
        android:fillColor="#424242"
        android:pathData="M 49,38 L 52,38 L 52,78 C 52,82 53,85 54,86 C 52,85 49,82 49,78 Z" />

    <path
        android:fillColor="#212121"
        android:pathData="M 57,38 L 59,38 L 59,78 C 59,82 58,85 54,88 C 56,86 57,82 57,78 Z" />

    <!-- Pen Nib (Gold Gradient) -->
    <path
        android:pathData="M 54,16 L 48,36 L 60,36 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="48"
                android:startY="16"
                android:endX="60"
                android:endY="36"
                android:type="linear">
                <item android:color="#FFEE58" android:offset="0.0"/>
                <item android:color="#F57F17" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>
    
    <!-- Nib Cut and details -->
    <path
        android:strokeColor="#111111"
        android:strokeWidth="1.5"
        android:pathData="M 54,16 L 54,28" />
        
    <path
        android:fillColor="#111111"
        android:pathData="M 54,28 C 55.5,28 56.5,29 56.5,30.5 C 56.5,32 55.5,33 54,33 C 52.5,33 51.5,32 51.5,30.5 C 51.5,29 52.5,28 54,28 Z" />

    <path
        android:strokeColor="#F57F17"
        android:strokeWidth="1"
        android:pathData="M 50,30 L 49,36 M 58,30 L 59,36" />

    <!-- Gold Rings -->
    <path
        android:fillColor="#FBC02D"
        android:pathData="M 48.5,36 L 59.5,36 L 59.5,38 L 48.5,38 Z" />
        
    <path
        android:fillColor="#FBC02D"
        android:pathData="M 49,60 L 59,60 L 59,63 L 49,63 Z" />

    <!-- Pen Clip (Gold) -->
    <path
        android:strokeColor="#FBC02D"
        android:strokeWidth="2.5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 59,42 L 63,42 L 63,65 C 63,68 60,69 59,69" />
        
    <path
        android:strokeColor="#FFF59D"
        android:strokeWidth="1"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 63,42 L 63,64" />

</vector>
XML

# Create a solid color background for adaptive icon
cat << 'XML' > "$RES_DIR/values/ic_launcher_background.xml"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#000000</color>
</resources>
XML

cat << 'XML' > "$RES_DIR/drawable/ic_launcher_background.xml"
<?xml version="1.0" encoding="utf-8"?>
<color xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/ic_launcher_background" />
XML

# Create Adaptive Icon config
mkdir -p "$RES_DIR/mipmap-anydpi-v26"
cat << 'XML' > "$RES_DIR/mipmap-anydpi-v26/ic_launcher.xml"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
XML

cat << 'XML' > "$RES_DIR/mipmap-anydpi-v26/ic_launcher_round.xml"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
XML

# Create Fallback Vectors for older devices (anydpi covers it for VectorDrawable supported devices)
mkdir -p "$RES_DIR/mipmap-anydpi"
# Flattened square icon
cat << 'XML' > "$RES_DIR/mipmap-anydpi/ic_launcher.xml"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#000000" android:pathData="M 0,0 L 108,0 L 108,108 L 0,108 Z" />
    <!-- Reference the same foreground shapes -->
    <path android:strokeWidth="10" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M 38,34 L 18,54 L 38,74">
        <aapt:attr name="android:strokeColor">
            <gradient android:startX="18" android:startY="34" android:endX="38" android:endY="74" android:type="linear">
                <item android:color="#FFF176" android:offset="0.0"/>
                <item android:color="#FFB300" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:strokeWidth="10" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M 70,34 L 90,54 L 70,74">
        <aapt:attr name="android:strokeColor">
            <gradient android:startX="70" android:startY="34" android:endX="90" android:endY="74" android:type="linear">
                <item android:color="#69F0AE" android:offset="0.0"/>
                <item android:color="#00E676" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:fillColor="#111111" android:pathData="M 49,38 L 59,38 L 59,78 C 59,85 54,88 54,88 C 54,88 49,85 49,78 Z" />
    <path android:fillColor="#424242" android:pathData="M 49,38 L 52,38 L 52,78 C 52,82 53,85 54,86 C 52,85 49,82 49,78 Z" />
    <path android:fillColor="#212121" android:pathData="M 57,38 L 59,38 L 59,78 C 59,82 58,85 54,88 C 56,86 57,82 57,78 Z" />
    <path android:pathData="M 54,16 L 48,36 L 60,36 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="48" android:startY="16" android:endX="60" android:endY="36" android:type="linear">
                <item android:color="#FFEE58" android:offset="0.0"/>
                <item android:color="#F57F17" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:strokeColor="#111111" android:strokeWidth="1.5" android:pathData="M 54,16 L 54,28" />
    <path android:fillColor="#111111" android:pathData="M 54,28 C 55.5,28 56.5,29 56.5,30.5 C 56.5,32 55.5,33 54,33 C 52.5,33 51.5,32 51.5,30.5 C 51.5,29 52.5,28 54,28 Z" />
    <path android:strokeColor="#F57F17" android:strokeWidth="1" android:pathData="M 50,30 L 49,36 M 58,30 L 59,36" />
    <path android:fillColor="#FBC02D" android:pathData="M 48.5,36 L 59.5,36 L 59.5,38 L 48.5,38 Z" />
    <path android:fillColor="#FBC02D" android:pathData="M 49,60 L 59,60 L 59,63 L 49,63 Z" />
    <path android:strokeColor="#FBC02D" android:strokeWidth="2.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M 59,42 L 63,42 L 63,65 C 63,68 60,69 59,69" />
    <path android:strokeColor="#FFF59D" android:strokeWidth="1" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M 63,42 L 63,64" />
</vector>
XML

# Flattened round icon
cat << 'XML' > "$RES_DIR/mipmap-anydpi/ic_launcher_round.xml"
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#000000" android:pathData="M 54,54 m -50,0 a 50,50 0 1,0 100,0 a 50,50 0 1,0 -100,0" />
    <!-- Reference the same foreground shapes -->
    <path android:strokeWidth="10" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M 38,34 L 18,54 L 38,74">
        <aapt:attr name="android:strokeColor">
            <gradient android:startX="18" android:startY="34" android:endX="38" android:endY="74" android:type="linear">
                <item android:color="#FFF176" android:offset="0.0"/>
                <item android:color="#FFB300" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:strokeWidth="10" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M 70,34 L 90,54 L 70,74">
        <aapt:attr name="android:strokeColor">
            <gradient android:startX="70" android:startY="34" android:endX="90" android:endY="74" android:type="linear">
                <item android:color="#69F0AE" android:offset="0.0"/>
                <item android:color="#00E676" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:fillColor="#111111" android:pathData="M 49,38 L 59,38 L 59,78 C 59,85 54,88 54,88 C 54,88 49,85 49,78 Z" />
    <path android:fillColor="#424242" android:pathData="M 49,38 L 52,38 L 52,78 C 52,82 53,85 54,86 C 52,85 49,82 49,78 Z" />
    <path android:fillColor="#212121" android:pathData="M 57,38 L 59,38 L 59,78 C 59,82 58,85 54,88 C 56,86 57,82 57,78 Z" />
    <path android:pathData="M 54,16 L 48,36 L 60,36 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="48" android:startY="16" android:endX="60" android:endY="36" android:type="linear">
                <item android:color="#FFEE58" android:offset="0.0"/>
                <item android:color="#F57F17" android:offset="1.0"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:strokeColor="#111111" android:strokeWidth="1.5" android:pathData="M 54,16 L 54,28" />
    <path android:fillColor="#111111" android:pathData="M 54,28 C 55.5,28 56.5,29 56.5,30.5 C 56.5,32 55.5,33 54,33 C 52.5,33 51.5,32 51.5,30.5 C 51.5,29 52.5,28 54,28 Z" />
    <path android:strokeColor="#F57F17" android:strokeWidth="1" android:pathData="M 50,30 L 49,36 M 58,30 L 59,36" />
    <path android:fillColor="#FBC02D" android:pathData="M 48.5,36 L 59.5,36 L 59.5,38 L 48.5,38 Z" />
    <path android:fillColor="#FBC02D" android:pathData="M 49,60 L 59,60 L 59,63 L 49,63 Z" />
    <path android:strokeColor="#FBC02D" android:strokeWidth="2.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M 59,42 L 63,42 L 63,65 C 63,68 60,69 59,69" />
    <path android:strokeColor="#FFF59D" android:strokeWidth="1" strokeLineCap="round" strokeLineJoin="round" android:pathData="M 63,42 L 63,64" />
</vector>
XML

echo "Done"
