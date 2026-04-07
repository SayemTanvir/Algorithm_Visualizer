package org.example.VisuAlgorithm;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;

/**
 * Lightweight animated GIF encoder.
 * Encodes a sequence of BufferedImage frames into an animated GIF file.
 *
 * Usage:
 *   AnimatedGifEncoder e = new AnimatedGifEncoder();
 *   e.start(outputFilePath);
 *   e.setDelay(100);   // ms per frame
 *   e.setRepeat(0);    // 0 = loop forever
 *   e.addFrame(image1);
 *   e.addFrame(image2);
 *   e.finish();
 */
public class AnimatedGifEncoder {

    private int    width;
    private int    height;
    private Color  transparent     = null;
    private int    transIndex       = 0;
    private int    repeat           = -1;
    private int    delay            = 16;
    private boolean started         = false;
    private OutputStream out;

    private BufferedImage image;
    private byte[]        pixels;
    private byte[]        indexedPixels;
    private int           colorDepth;
    private byte[]        colorTab;
    private boolean[]     usedEntry    = new boolean[256];
    private int           palSize      = 7;
    private int           dispose      = -1;
    private boolean       closeStream  = false;
    private boolean       firstFrame   = true;
    private boolean       sizeSet      = false;
    private int           sample       = 10;

    public void setDelay(int ms)    { delay = Math.round(ms / 10.0f); }
    public void setRepeat(int iter) { repeat = (iter < 0) ? -1 : iter; }
    public void setTransparent(Color c) { transparent = c; }
    public void setQuality(int quality) { sample = Math.max(1, quality); }

    public boolean addFrame(BufferedImage im) {
        if (im == null || !started) return false;
        boolean ok = true;
        try {
            if (!sizeSet) setSize(im.getWidth(), im.getHeight());
            image = im;
            getImagePixels();
            analyzePixels();
            if (firstFrame) {
                writeLSD();
                writePalette();
                if (repeat >= 0) writeNetscapeExt();
            }
            writeGraphicCtrlExt();
            writeImageDesc();
            if (!firstFrame) writePalette();
            writePixels();
            firstFrame = false;
        } catch (IOException e) { ok = false; }
        return ok;
    }

    public boolean finish() {
        if (!started) return false;
        boolean ok = true;
        started = false;
        try {
            out.write(0x3b);
            out.flush();
            if (closeStream) out.close();
        } catch (IOException e) { ok = false; }
        transIndex = 0;
        out = null;
        image = null;
        pixels = null;
        indexedPixels = null;
        colorTab = null;
        closeStream = false;
        firstFrame = true;
        return ok;
    }

    public void setSize(int w, int h) {
        if (!started || firstFrame) {
            width  = w;
            height = h;
            if (width  < 1) width  = 320;
            if (height < 1) height = 240;
            sizeSet = true;
        }
    }

    public boolean start(String file) {
        boolean ok;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            ok = start(out);
            closeStream = true;
        } catch (IOException e) { ok = false; }
        return ok;
    }

    public boolean start(OutputStream os) {
        if (os == null) return false;
        boolean ok = true;
        closeStream = false;
        out = os;
        try {
            writeString("GIF89a");
        } catch (IOException e) { ok = false; }
        return started = ok;
    }

    private void analyzePixels() {
        int len = pixels.length;
        int nPix = len / 3;
        indexedPixels = new byte[nPix];
        NeuQuant nq = new NeuQuant(pixels, len, sample);
        colorTab = nq.process();
        // convert map from BGR to RGB
        for (int i = 0; i < colorTab.length; i += 3) {
            byte tmp = colorTab[i];
            colorTab[i]     = colorTab[i + 2];
            colorTab[i + 2] = tmp;
            usedEntry[i / 3] = false;
        }
        int k = 0;
        for (int i = 0; i < nPix; i++) {
            int index = nq.map(pixels[k++] & 0xff, pixels[k++] & 0xff, pixels[k++] & 0xff);
            usedEntry[index] = true;
            indexedPixels[i] = (byte) index;
        }
        pixels = null;
        colorDepth = 8;
        palSize    = 7;
        if (transparent != null) {
            transIndex = findClosest(transparent);
        }
    }

    private int findClosest(Color c) {
        if (colorTab == null) return -1;
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        int minpos = 0, dmin = 256 * 256 * 256;
        int len = colorTab.length;
        for (int i = 0; i < len;) {
            int dr = r - (colorTab[i++] & 0xff);
            int dg = g - (colorTab[i++] & 0xff);
            int db = b - (colorTab[i++] & 0xff);
            int d = dr*dr + dg*dg + db*db;
            int index = i/3 - 1;
            if (usedEntry[index] && d < dmin) { dmin = d; minpos = index; }
        }
        return minpos;
    }

    private void getImagePixels() {
        int w = image.getWidth(), h = image.getHeight();
        int type = image.getType();
        if (w != width || h != height || type != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = temp.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            image = temp;
        }
        pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    }

    private void writeGraphicCtrlExt() throws IOException {
        out.write(0x21); out.write(0xf9); out.write(4);
        int transp, disp;
        if (transparent == null) { transp = 0; disp = 0; }
        else                     { transp = 1; disp = 2; }
        if (dispose >= 0) disp = dispose & 7;
        disp <<= 2;
        out.write(0 | disp | 0 | transp);
        writeShort(delay);
        out.write(transIndex);
        out.write(0);
    }

    private void writeImageDesc() throws IOException {
        out.write(0x2c);
        writeShort(0); writeShort(0);
        writeShort(width); writeShort(height);
        if (firstFrame) out.write(0);
        else            out.write(0x80 | palSize);
    }

    private void writeLSD() throws IOException {
        writeShort(width); writeShort(height);
        out.write(0x80 | 0x70 | 0x00 | palSize);
        out.write(0); out.write(0);
    }

    private void writeNetscapeExt() throws IOException {
        out.write(0x21); out.write(0xff); out.write(11);
        writeString("NETSCAPE2.0");
        out.write(3); out.write(1);
        writeShort(repeat);
        out.write(0);
    }

    private void writePalette() throws IOException {
        out.write(colorTab);
        int n = (3 * 256) - colorTab.length;
        for (int i = 0; i < n; i++) out.write(0);
    }

    private void writePixels() throws IOException {
        LZWEncoder encoder = new LZWEncoder(width, height, indexedPixels, colorDepth);
        encoder.encode(out);
    }

    private void writeShort(int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private void writeString(String s) throws IOException {
        for (char c : s.toCharArray()) out.write((byte) c);
    }

    // ---- Embedded NeuQuant colour quantiser ----
    static class NeuQuant {
        private static final int netsize   = 256;
        private static final int prime1    = 499, prime2 = 491, prime3 = 487, prime4 = 503;
        private static final int minpicturebytes = 3 * prime4;
        private static final int maxnetpos  = netsize - 1;
        private static final int netbiasshift = 4;
        private static final int ncycles    = 100;
        private static final int intbiasshift = 16;
        private static final int intbias    = 1 << intbiasshift;
        private static final int gammashift = 10, gamma = 1 << gammashift;
        private static final int betashift  = 10, beta = intbias >> betashift, betagamma = intbias << (gammashift - betashift);
        private static final int initrad    = netsize >> 3, radiusbiasshift = 6, radiusbias = 1 << radiusbiasshift;
        private static final int initradius = initrad * radiusbias;
        private static final int radiusdec  = 30;
        private static final int alphabiasshift = 10, initalpha = 1 << alphabiasshift;
        private        int alphadec;
        private static final int radbiasshift = 8, radbias = 1 << radbiasshift;
        private static final int alpharadbshift = alphabiasshift + radbiasshift, alpharadbias = 1 << alpharadbshift;

        private byte[] thepicture;
        private int    lengthcount, samplefac;
        private int[][] network = new int[netsize][4];
        private int[]   netindex = new int[256];
        private int[]   bias     = new int[netsize];
        private int[]   freq     = new int[netsize];
        private int[]   radpower = new int[initrad];

        NeuQuant(byte[] thepicture, int length, int sample) {
            this.thepicture  = thepicture;
            this.lengthcount = length;
            this.samplefac   = sample;
            for (int i = 0; i < netsize; i++) {
                int[] p = network[i];
                p[0] = p[1] = p[2] = (i << (netbiasshift + 8)) / netsize;
                freq[i] = intbias / netsize;
                bias[i] = 0;
            }
        }

        byte[] process() {
            learn(); unbiasnet(); inxbuild();
            return colormap();
        }

        byte[] colormap() {
            byte[] map = new byte[3 * netsize];
            int[] index = new int[netsize];
            for (int i = 0; i < netsize; i++) index[network[i][3]] = i;
            int k = 0;
            for (int i = 0; i < netsize; i++) {
                int j = index[i];
                map[k++] = (byte) network[j][0];
                map[k++] = (byte) network[j][1];
                map[k++] = (byte) network[j][2];
            }
            return map;
        }

        void inxbuild() {
            int previouscol = 0, startpos = 0;
            for (int i = 0; i < netsize; i++) {
                int[] p = network[i];
                int smallpos = i, smallval = p[1];
                for (int j = i + 1; j < netsize; j++) {
                    if (network[j][1] < smallval) { smallpos = j; smallval = network[j][1]; }
                }
                int[] q = network[smallpos];
                if (i != smallpos) {
                    int j = q[0]; q[0] = p[0]; p[0] = j;
                    j = q[1]; q[1] = p[1]; p[1] = j;
                    j = q[2]; q[2] = p[2]; p[2] = j;
                    j = q[3]; q[3] = p[3]; p[3] = j;
                }
                if (smallval != previouscol) {
                    netindex[previouscol] = (startpos + i) >> 1;
                    for (int j = previouscol + 1; j < smallval; j++) netindex[j] = i;
                    previouscol = smallval;
                    startpos = i;
                }
            }
            netindex[previouscol] = (startpos + maxnetpos) >> 1;
            for (int j = previouscol + 1; j < 256; j++) netindex[j] = maxnetpos;
        }

        void learn() {
            if (lengthcount < minpicturebytes) samplefac = 1;
            alphadec = 30 + ((samplefac - 1) / 3);
            int pix = 0, lim = lengthcount, samplepixels = lengthcount / (3 * samplefac);
            int delta = samplepixels / ncycles, alpha = initalpha, radius = initradius;
            int rad = radius >> radiusbiasshift;
            if (rad <= 1) rad = 0;
            for (int i = 0; i < rad; i++)
                radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad));
            int step;
            if (lengthcount < minpicturebytes) step = 3;
            else if (lengthcount % prime1 != 0) step = 3 * prime1;
            else if (lengthcount % prime2 != 0) step = 3 * prime2;
            else if (lengthcount % prime3 != 0) step = 3 * prime3;
            else step = 3 * prime4;
            int i = 0;
            for (int b = 0; b < samplepixels; ) {
                int blue  = thepicture[pix]   & 0xff;
                int green = thepicture[pix+1] & 0xff;
                int red   = thepicture[pix+2] & 0xff;
                int j = contest(blue, green, red);
                altersingle(alpha, j, blue, green, red);
                if (rad != 0) alterneigh(rad, j, blue, green, red);
                pix += step;
                if (pix >= lim) pix -= lengthcount;
                b++;
                if (delta == 0) delta = 1;
                if (b % delta == 0) {
                    alpha -= alpha / alphadec;
                    radius -= radius / radiusdec;
                    rad = radius >> radiusbiasshift;
                    if (rad <= 1) rad = 0;
                    for (i = 0; i < rad; i++)
                        radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad));
                }
            }
        }

        void unbiasnet() {
            for (int i = 0; i < netsize; i++) {
                network[i][0] >>= netbiasshift;
                network[i][1] >>= netbiasshift;
                network[i][2] >>= netbiasshift;
                network[i][3] = i;
            }
        }

        void alterneigh(int rad, int i, int b, int g, int r) {
            int lo = i - rad; if (lo < -1) lo = -1;
            int hi = i + rad; if (hi > netsize) hi = netsize;
            int j = i + 1, k = i - 1, m = 1;
            while (j < hi || k > lo) {
                int a = radpower[m++];
                if (j < hi) { int[] p = network[j++]; p[0] -= (a*(p[0]-b))/alpharadbias; p[1] -= (a*(p[1]-g))/alpharadbias; p[2] -= (a*(p[2]-r))/alpharadbias; }
                if (k > lo) { int[] p = network[k--]; p[0] -= (a*(p[0]-b))/alpharadbias; p[1] -= (a*(p[1]-g))/alpharadbias; p[2] -= (a*(p[2]-r))/alpharadbias; }
            }
        }

        void altersingle(int alpha, int i, int b, int g, int r) {
            int[] n = network[i];
            n[0] -= (alpha * (n[0] - b)) / initalpha;
            n[1] -= (alpha * (n[1] - g)) / initalpha;
            n[2] -= (alpha * (n[2] - r)) / initalpha;
        }

        int contest(int b, int g, int r) {
            int bestd = Integer.MAX_VALUE, bestbiasd = bestd, bestpos = -1, bestbiaspos = 0;
            for (int i = 0; i < netsize; i++) {
                int[] n = network[i];
                int dist = Math.abs(n[0]-b) + Math.abs(n[1]-g) + Math.abs(n[2]-r);
                if (dist < bestd) { bestd = dist; bestpos = i; }
                int biasdist = dist - (bias[i] >> (intbiasshift - netbiasshift));
                if (biasdist < bestbiasd) { bestbiasd = biasdist; bestbiaspos = i; }
                freq[i]  -= freq[i]  / 1024;
                bias[i]  += freq[i];
            }
            freq[bestpos] += beta;
            bias[bestpos] -= betagamma;
            return bestbiaspos;
        }

        int map(int b, int g, int r) {
            int bestd = 1000, bestpos = -1;
            int i = netindex[g], j = i - 1;
            while (i < netsize || j >= 0) {
                if (i < netsize) {
                    int[] p = network[i];
                    int dist = p[1] - g;
                    if (dist >= bestd) i = netsize;
                    else { i++; if (dist < 0) dist = -dist; int a = Math.abs(p[0]-b) + Math.abs(p[2]-r); dist += a; if (dist < bestd) { bestd = dist; bestpos = p[3]; } }
                }
                if (j >= 0) {
                    int[] p = network[j];
                    int dist = g - p[1];
                    if (dist >= bestd) j = -1;
                    else { j--; if (dist < 0) dist = -dist; int a = Math.abs(p[0]-b) + Math.abs(p[2]-r); dist += a; if (dist < bestd) { bestd = dist; bestpos = p[3]; } }
                }
            }
            return bestpos;
        }
    }

    // ---- Embedded LZW Encoder ----
    static class LZWEncoder {
        private static final int EOF = -1;
        private int imgW, imgH;
        private byte[] pixAry;
        private int initCodeSize;
        private int remaining, curPixel;

        static final int BITS  = 12;
        static final int HSIZE = 5003;

        int  n_bits, maxbits = BITS, maxcode, maxmaxcode = 1 << BITS;
        int[] htab = new int[HSIZE], codetab = new int[HSIZE];
        int hsize = HSIZE, free_ent = 0;
        boolean clear_flg = false;
        int clear_code, eof_code;
        int cur_accum = 0, cur_bits = 0;
        int[] masks = {0x0000,0x0001,0x0003,0x0007,0x000F,0x001F,0x003F,0x007F,0x00FF,0x01FF,0x03FF,0x07FF,0x0FFF,0x1FFF,0x3FFF,0x7FFF,0xFFFF};
        int a_count;
        byte[] accum = new byte[256];

        LZWEncoder(int width, int height, byte[] pixels, int color_depth) {
            imgW = width; imgH = height; pixAry = pixels; initCodeSize = Math.max(2, color_depth);
        }

        void char_out(byte c, OutputStream outs) throws IOException {
            accum[a_count++] = c;
            if (a_count >= 254) flush_char(outs);
        }

        void cl_block(OutputStream outs) throws IOException {
            cl_hash(hsize); free_ent = clear_code + 2; clear_flg = true; output(clear_code, outs);
        }

        void cl_hash(int hsize) { for (int i = 0; i < hsize; i++) htab[i] = -1; }

        void compress(int init_bits, OutputStream outs) throws IOException {
            int fcode, c, i, ent, disp, hsize_reg, hshift;
            n_bits = init_bits; maxcode = (1 << n_bits) - 1;
            clear_code = 1 << (init_bits - 1); eof_code = clear_code + 1; free_ent = clear_code + 2;
            a_count = 0; ent = nextPixel();
            hshift = 0; for (fcode = hsize; fcode < 65536; fcode *= 2) hshift++;
            hshift = 8 - hshift; hsize_reg = hsize; cl_hash(hsize_reg); output(clear_code, outs);
            outer_loop: while ((c = nextPixel()) != EOF) {
                fcode = (c << maxbits) + ent; i = (c << hshift) ^ ent;
                if (htab[i] == fcode) { ent = codetab[i]; continue; }
                else if (htab[i] >= 0) {
                    disp = hsize_reg - i; if (i == 0) disp = 1;
                    do { if ((i -= disp) < 0) i += hsize_reg; if (htab[i] == fcode) { ent = codetab[i]; continue outer_loop; } } while (htab[i] >= 0);
                }
                output(ent, outs); ent = c;
                if (free_ent < maxmaxcode) { codetab[i] = free_ent++; htab[i] = fcode; }
                else cl_block(outs);
            }
            output(ent, outs); output(eof_code, outs);
        }

        void encode(OutputStream os) throws IOException {
            os.write(initCodeSize); remaining = imgW * imgH; curPixel = 0;
            compress(initCodeSize + 1, os); os.write(0);
        }

        void flush_char(OutputStream outs) throws IOException {
            if (a_count > 0) { outs.write(a_count); outs.write(accum, 0, a_count); a_count = 0; }
        }

        int nextPixel() {
            if (remaining == 0) return EOF;
            remaining--;
            return pixAry[curPixel++] & 0xff;
        }

        void output(int code, OutputStream outs) throws IOException {
            cur_accum &= masks[cur_bits]; if (cur_bits > 0) cur_accum |= (code << cur_bits); else cur_accum = code;
            cur_bits += n_bits;
            while (cur_bits >= 8) { char_out((byte)(cur_accum & 0xff), outs); cur_accum >>= 8; cur_bits -= 8; }
            if (free_ent > maxcode || clear_flg) {
                if (clear_flg) { maxcode = (1 << (n_bits = initCodeSize + 1)) - 1; clear_flg = false; }
                else { n_bits++; maxcode = (n_bits == maxbits) ? maxmaxcode : (1 << n_bits) - 1; }
            }
            if (code == eof_code) { while (cur_bits > 0) { char_out((byte)(cur_accum & 0xff), outs); cur_accum >>= 8; cur_bits -= 8; } flush_char(outs); }
        }
    }
}
