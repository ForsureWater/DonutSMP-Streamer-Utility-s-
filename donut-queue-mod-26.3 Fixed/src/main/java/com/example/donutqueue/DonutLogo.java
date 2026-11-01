package com.example.donutqueue;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Donut logo drawn with plain fill() calls (no texture registration needed). Generated from the supplied image with the white background removed. */
final class DonutLogo {
    private DonutLogo() {}

    static final int SIZE = 26;

    // each row: {x0, x1, argb, x0, x1, argb, ...}
    private static final int[][] ROWS = {
        {9, 12, 0xFF1E161A, 12, 16, 0xFF0B0F2D, 16, 17, 0xFF1E1A1C},
        {7, 9, 0xFF1B181F, 9, 10, 0xFF081D65, 10, 11, 0xFF04319D, 11, 15, 0xFF043FC0, 15, 16, 0xFF04329E, 16, 17, 0xFF071D65, 17, 19, 0xFF0A0F2D},
        {6, 7, 0xFF110D20, 7, 8, 0xFF061F6A, 8, 9, 0xFF0547D1, 9, 17, 0xFF0463FF, 17, 18, 0xFF0447D1, 18, 19, 0xFF061E6A, 19, 20, 0xFF0F0F21},
        {5, 6, 0xFF0C0D23, 6, 7, 0xFF062B93, 7, 19, 0xFF045FFF, 19, 20, 0xFF052D95, 20, 21, 0xFF0B0C24},
        {3, 4, 0xFF20223C, 4, 5, 0xFF0B091F, 5, 6, 0xFF062A8E, 6, 20, 0xFF0462FF, 20, 21, 0xFF062B90, 21, 22, 0xFF07091C},
        {3, 4, 0xFF09091D, 4, 5, 0xFF06185A, 5, 21, 0xFF075AF6, 21, 22, 0xFF041859, 22, 23, 0xFF0A091D},
        {2, 4, 0xFF060B26, 4, 5, 0xFF063FBC, 5, 11, 0xFF026DFF, 11, 16, 0xFF1191FF, 16, 21, 0xFF036EFA, 21, 22, 0xFF063EBE, 22, 24, 0xFF02041D},
        {1, 2, 0xFF121731, 2, 3, 0xFF2D2028, 3, 4, 0xFF0D1441, 4, 5, 0xFF0551E8, 5, 9, 0xFF0170FE, 9, 10, 0xFF1595FF, 10, 11, 0xFF137EC6, 11, 15, 0xFF084F87, 15, 16, 0xFF147DC4, 16, 17, 0xFF1496FF, 17, 21, 0xFF026CFB, 21, 22, 0xFF0252E9, 22, 23, 0xFF0B1440, 23, 24, 0xFF2B1F26, 24, 25, 0xFF141B34},
        {1, 2, 0xFF1B1A2B, 2, 3, 0xFF7C5134, 3, 4, 0xFF071655, 4, 8, 0xFF0559F2, 8, 9, 0xFF087DFE, 9, 10, 0xFF1176BF, 10, 11, 0xFF331F22, 11, 15, 0xFF824B2E, 15, 16, 0xFF311F22, 16, 17, 0xFF1077BF, 17, 21, 0xFF087DFE, 21, 22, 0xFF0458F3, 22, 23, 0xFF051653, 23, 24, 0xFF7D5135, 24, 25, 0xFF1C192D},
        {0, 1, 0xFF000828, 1, 3, 0xFF563C3B, 3, 4, 0xFF001C7D, 4, 8, 0xFF0662FB, 8, 9, 0xFF0B86FF, 9, 10, 0xFF08487F, 10, 11, 0xFF975B36, 11, 12, 0xFFFFB981, 12, 15, 0xFFF1A877, 15, 16, 0xFF985C38, 16, 17, 0xFF074980, 17, 18, 0xFF0A87FF, 18, 22, 0xFF006AF9, 22, 23, 0xFF001D7E, 23, 25, 0xFF603C26, 25, 26, 0xFF020A2F},
        {0, 1, 0xFF0D112B, 1, 2, 0xFF2C1E27, 2, 3, 0xFF111336, 3, 4, 0xFF0347D6, 4, 9, 0xFF036EFE, 9, 10, 0xFF173F62, 10, 11, 0xFFA9704B, 11, 12, 0xFF5E4333, 12, 14, 0xFF3D3430, 14, 15, 0xFF594132, 15, 16, 0xFFA76C49, 16, 17, 0xFF183D62, 17, 22, 0xFF0986FD, 22, 23, 0xFF0347D6, 23, 24, 0xFF121337, 24, 25, 0xFF2D1E28, 25, 26, 0xFF0D122F},
        {0, 1, 0xFF13152F, 1, 2, 0xFF000019, 2, 3, 0xFF0432A6, 3, 8, 0xFF045FFC, 8, 9, 0xFF0574DB, 9, 11, 0xFF4C3935, 11, 12, 0xFF8D959C, 12, 14, 0xFFEBEDEE, 14, 15, 0xFF959DA4, 15, 17, 0xFF443326, 17, 18, 0xFF0774DA, 18, 23, 0xFF0373FF, 23, 24, 0xFF0333A8, 24, 25, 0xFF00001B, 25, 26, 0xFF11142E},
        {0, 2, 0xFF080A24, 2, 3, 0xFF0641D0, 3, 4, 0xFF0257EF, 4, 8, 0xFF0273FE, 8, 9, 0xFF0D82EA, 9, 11, 0xFF2E2C35, 11, 15, 0xFFF6FBFD, 15, 17, 0xFF2F2520, 17, 18, 0xFF0E83E9, 18, 23, 0xFF026FFE, 23, 24, 0xFF0342CF, 24, 26, 0xFF080B28},
        {0, 2, 0xFF050825, 2, 3, 0xFF0633A0, 3, 5, 0xFF014EF1, 5, 9, 0xFF0372FF, 9, 10, 0xFF1676BF, 10, 11, 0xFF06101C, 11, 12, 0xFF231B16, 12, 14, 0xFF77716E, 14, 15, 0xFF271F1B, 15, 16, 0xFF060F1A, 16, 17, 0xFF1676BD, 17, 21, 0xFF087EFF, 21, 23, 0xFF0461F2, 23, 24, 0xFF0634A1, 24, 26, 0xFF000118},
        {0, 1, 0xFF0F122C, 1, 2, 0xFF49312C, 2, 3, 0xFF080E31, 3, 5, 0xFF0343CE, 5, 10, 0xFF0465F5, 10, 11, 0xFF1481E2, 11, 12, 0xFF094677, 12, 14, 0xFF001835, 14, 15, 0xFF074676, 15, 16, 0xFF1481E0, 16, 20, 0xFF077DFF, 20, 21, 0xFF0462F5, 21, 23, 0xFF004AE8, 23, 24, 0xFF070E33, 24, 25, 0xFF49312E, 25, 26, 0xFF0F112D},
        {0, 1, 0xFF030B2A, 1, 2, 0xFF7D5441, 2, 3, 0xFF5F3F2D, 3, 4, 0xFF001455, 4, 6, 0xFF034CE9, 6, 20, 0xFF0371FF, 20, 22, 0xFF0459EF, 22, 23, 0xFF001257, 23, 24, 0xFF5F3F2D, 24, 25, 0xFF7E5542, 25, 26, 0xFF020827},
        {0, 1, 0xFF050D2D, 1, 2, 0xFF3E2B2F, 2, 3, 0xFFCF8F61, 3, 4, 0xFF1D192F, 4, 5, 0xFF013AC1, 5, 21, 0xFF045AF3, 21, 22, 0xFF013AC4, 22, 23, 0xFF1C1A2D, 23, 24, 0xFFD08F5F, 24, 25, 0xFF3F2B2F, 25, 26, 0xFF050F2B},
        {1, 2, 0xFF0A0B23, 2, 3, 0xFFBA7C53, 3, 4, 0xFF563B33, 4, 5, 0xFF012A9D, 5, 9, 0xFF034FED, 9, 20, 0xFF046AFB, 20, 21, 0xFF054EED, 21, 22, 0xFF012A9F, 22, 23, 0xFF553B34, 23, 24, 0xFFB77C53, 24, 25, 0xFF0A0C22},
        {1, 2, 0xFF000926, 2, 4, 0xFF64453B, 4, 5, 0xFF001972, 5, 6, 0xFF0756FA, 6, 10, 0xFF0048E2, 10, 16, 0xFF056BFC, 16, 21, 0xFF024DE4, 21, 22, 0xFF001971, 22, 24, 0xFF744A31, 24, 25, 0xFF000728},
        {2, 3, 0xFF0E0E26, 3, 4, 0xFF8A5C3E, 4, 5, 0xFF1B182A, 5, 6, 0xFF002288, 6, 8, 0xFF0043D4, 8, 12, 0xFF024FEF, 12, 15, 0xFF036DFE, 15, 20, 0xFF034EE4, 20, 21, 0xFF00228A, 21, 22, 0xFF1C172B, 22, 23, 0xFF895E40, 23, 24, 0xFF0E0F27},
        {2, 3, 0xFF090E29, 3, 4, 0xFF2B202D, 4, 5, 0xFF9D6A43, 5, 6, 0xFF4A3028, 6, 7, 0xFF151735, 7, 8, 0xFF001153, 8, 9, 0xFF002D9F, 9, 17, 0xFF044FEA, 17, 18, 0xFF002D9F, 18, 19, 0xFF001151, 19, 20, 0xFF161734, 20, 21, 0xFF4A302A, 21, 22, 0xFF9F6A44, 22, 23, 0xFF2C212D, 23, 24, 0xFF09102C},
        {3, 4, 0xFF030A27, 4, 5, 0xFF36272E, 5, 7, 0xFFC28452, 7, 8, 0xFFA37154, 8, 9, 0xFF39282B, 9, 10, 0xFF001258, 10, 11, 0xFF0143D1, 11, 15, 0xFF044FF2, 15, 16, 0xFF0243D1, 16, 17, 0xFF00125A, 17, 18, 0xFF39292C, 18, 19, 0xFFA27152, 19, 21, 0xFFCC8A5D, 21, 22, 0xFF36292F, 22, 23, 0xFF020A27},
        {4, 5, 0xFF060E2B, 5, 6, 0xFF221A2B, 6, 7, 0xFF996541, 7, 9, 0xFFE19760, 9, 10, 0xFF73513F, 10, 11, 0xFF0C102F, 11, 15, 0xFF001869, 15, 16, 0xFF0B102E, 16, 17, 0xFF71503E, 17, 19, 0xFFE49F6D, 19, 20, 0xFF986442, 20, 21, 0xFF221A28, 21, 22, 0xFF030A28},
        {5, 7, 0xFF0F1432, 7, 8, 0xFF4B3331, 8, 9, 0xFF9B6540, 9, 10, 0xFFD38E57, 10, 11, 0xFFB77B4B, 11, 12, 0xFF7F5030, 12, 14, 0xFF65412B, 14, 15, 0xFF7F5032, 15, 16, 0xFFB67A4C, 16, 17, 0xFFD38D58, 17, 18, 0xFF9A643F, 18, 19, 0xFF4A3430, 19, 21, 0xFF0B0B26},
        {7, 9, 0xFF080D2D, 9, 10, 0xFF2D202C, 10, 11, 0xFF5D3F34, 11, 15, 0xFF815840, 15, 16, 0xFF5E3F37, 16, 17, 0xFF2D202C, 17, 19, 0xFF0C0D28},
        {9, 17, 0xFF0F1333},
    };

    // recoloured copy of ROWS for the current menu colour (rebuilt only when the colour changes)
    private static int[][] tinted = null;
    private static int tintedFor = -1;

    /** Hue of the original frosting (the blue 0463FF). */
    private static final float BASE_HUE = 0.6f;

    /** Moves every blue pixel (frosting + its dark outline) onto the menu colour; dough, icing hole and sprinkles keep their colours. */
    private static int[][] rowsFor() {
        if (Theme.isDefaultAccent()) return ROWS;
        int key = Theme.neon & 0xFFFFFF;
        if (tinted != null && tintedFor == key) return tinted;
        float[] t = Theme.rgbToHsb(key);
        float th = t[0];
        float ts = t[1];
        int[][] out = new int[ROWS.length][];
        for (int i = 0; i < ROWS.length; i++) {
            int[] r = ROWS[i].clone();
            for (int k = 2; k < r.length; k += 3) {
                float[] hsb = Theme.rgbToHsb(r[k] & 0xFFFFFF);
                boolean blue = hsb[0] >= 0.5f && hsb[0] <= 0.72f && hsb[1] > 0.25f;
                if (!blue) continue;
                float nh = th + (hsb[0] - BASE_HUE) * 0.5f;   // keep a little of the shading variation
                float ns = Math.min(1f, hsb[1] * (ts / 0.97f));
                r[k] = 0xFF000000 | Theme.hsbToRgb(nh, ns, hsb[2]);
            }
            out[i] = r;
        }
        tinted = out;
        tintedFor = key;
        return out;
    }

    /** Draws the logo with its top-left at (x, y); each logo pixel is `scale` GUI pixels. */
    static void draw(GuiGraphicsExtractor g, int x, int y, int scale) {
        int[][] rows = rowsFor();
        for (int row = 0; row < rows.length; row++) {
            int[] r = rows[row];
            for (int i = 0; i + 2 < r.length; i += 3) {
                g.fill(x + r[i] * scale, y + row * scale, x + r[i + 1] * scale, y + (row + 1) * scale, r[i + 2]);
            }
        }
    }
}
