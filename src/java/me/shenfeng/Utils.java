package me.shenfeng;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.handler.codec.compression.ZlibWrapper.GZIP;
import static org.jboss.netty.handler.codec.compression.ZlibWrapper.ZLIB_OR_NONE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_ENCODING;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.util.CharsetUtil.UTF_8;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class Utils {

    public static byte[] toBytes(int i) {
        return new byte[] { (byte) (i >> 8), (byte) (i & 0x00ff) };
    }

    public static int toInt(byte[] bytes) {
        return toInt(bytes, 0);
    }

    public static int toInt(byte[] bytes, int start) {
        return (toInt(bytes[start]) << 8) + toInt(bytes[start + 1]);
    }

    public static int toInt(int b) {
        if (b < 0)
            b += 256;
        return b;
    }

    public static boolean isIP(String host) {
        for (int i = 0; i < host.length(); ++i) {
            if (!(Character.isDigit(host.charAt(i)) || host.charAt(i) == '.')) {
                return false;
            }
        }
        return true;
    }

    private static final String CS = "charset=";
    private static final char Q = '"';

    public static String getPath(URI uri) {
        String path = uri.getPath();
        String query = uri.getRawQuery();
        if ("".equals(path))
            path = "/";
        if (query == null)
            return path;
        else
            return path + "?" + query;
    }

    public static List getNameServer() {
        return sun.net.dns.ResolverConfiguration.open().nameservers();
    }

    public static int getPort(URI uri) {
        int port = uri.getPort();
        if (port == -1) {
            port = 80;
        }
        return port;
    }

    public static Charset parseCharset(String type) {
        try {
            type = type.toLowerCase();
            int i = type.indexOf(CS);
            if (i != -1) {
                String charset = type.substring(i + CS.length()).trim();
                return Charset.forName(charset);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    static Charset detectCharset(HttpResponse resp) {
        String type = resp.getHeader(CONTENT_TYPE);
        Charset result = null;
        if (type != null) {
            result = parseCharset(type);
        }
        if (result == null) {
            // decode a little the find Content-Type
            ChannelBuffer buffer = resp.getContent();
            byte[] array = buffer.array();
            int length = Math.min(350, array.length);
            String s = new String(array, 0, length, UTF_8);
            int idx = s.indexOf(CONTENT_TYPE);
            if (idx != -1) {
                int start = s.indexOf(Q, idx + CONTENT_TYPE.length() + 2);
                if (start != -1) {
                    start += 1;
                    int end = s.indexOf(Q, start);
                    if (end != -1) {
                        result = parseCharset(s.substring(start, end));
                    }
                }
            }
        }
        return result;
    }

    public static String bodyString(HttpResponse m) {
        String contentEncoding = m.getHeader(CONTENT_ENCODING);
        DecoderEmbedder<ChannelBuffer> decoder = null;
        if ("gzip".equalsIgnoreCase(contentEncoding)
                || "x-gzip".equalsIgnoreCase(contentEncoding)) {
            decoder = new DecoderEmbedder<ChannelBuffer>(
                    new ZlibDecoder(GZIP));
        } else if ("deflate".equalsIgnoreCase(contentEncoding)
                || "x-deflate".equalsIgnoreCase(contentEncoding)) {
            decoder = new DecoderEmbedder<ChannelBuffer>(new ZlibDecoder(
                    ZLIB_OR_NONE));
        }

        ChannelBuffer buffer = m.getContent();
        if (decoder != null) {
            decoder.offer(buffer);
            ChannelBuffer b = wrappedBuffer(decoder
                    .pollAll(new ChannelBuffer[decoder.size()]));
            if (decoder.finish()) {
                ChannelBuffer r = wrappedBuffer(decoder
                        .pollAll(new ChannelBuffer[decoder.size()]));
                buffer = wrappedBuffer(b, r);
            } else {
                buffer = b;
            }
        }
        m.setContent(buffer);// for detect charset
        Charset ch = detectCharset(m);
        if (ch == null)
            ch = UTF_8;
        return new String(buffer.array(), 0, buffer.readableBytes(), ch);
    }
}