package br.com.tclnova.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servidor local temporário. Não é serviço Android nem inicia em background.
 * O token é temporário; HTTP só deve ser usado numa rede local confiável.
 */
public final class UploadServer {
    public interface OnImage { void ready(File image); }
    private static final int MAX_INPUT_BYTES = 6 * 1024 * 1024;
    private static final long TTL_MS = 3 * 60 * 1000;
    private final File folder;
    private final OnImage listener;
    private final String token;
    private final ServerSocket server;
    private final String url;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean delivered = new AtomicBoolean(false);
    private Thread thread;

    public UploadServer(File folder, String ip, OnImage listener) throws Exception {
        this.folder = folder;
        this.listener = listener;
        byte[] nonce = new byte[20];
        new SecureRandom().nextBytes(nonce);
        StringBuilder hex = new StringBuilder();
        for (byte b : nonce) hex.append(String.format(Locale.US, "%02x", b & 0xff));
        token = hex.toString();
        server = new ServerSocket(0, 2, InetAddress.getByName(ip));
        server.setSoTimeout(2500);
        url = "http://" + ip + ":" + server.getLocalPort() + "/?token=" + token;
    }

    public String getUrl() { return url; }

    public void start() {
        thread = new Thread(this::serve, "NovaWallpaperReceiver");
        thread.setDaemon(true);
        thread.start();
    }

    public void close() {
        running.set(false);
        try { server.close(); } catch (IOException ignored) { }
    }

    private void serve() {
        long expire = System.currentTimeMillis() + TTL_MS;
        while (running.get() && !delivered.get() && System.currentTimeMillis() < expire) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(12000);
                handle(socket);
            } catch (SocketTimeoutException ignored) {
                // Timeout curto permite encerrar sem serviço persistente.
            } catch (IOException e) {
                if (running.get()) Log.w("TCLNova", "Conexão encerrada", e);
            }
        }
        close();
    }

    private void handle(Socket socket) throws IOException {
        InputStream in = new BufferedInputStream(socket.getInputStream());
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        String first = readLine(in);
        if (first == null) return;
        String[] request = first.split(" ");
        if (request.length != 3) { respond(out, 400, "Requisição inválida"); return; }
        String method = request[0], uri = request[1];
        String contentType = "";
        int length = -1, count = 0;
        while (true) {
            String header = readLine(in);
            if (header == null) return;
            if (header.isEmpty()) break;
            if (++count > 40) { respond(out, 431, "Cabeçalhos demais"); return; }
            String low = header.toLowerCase(Locale.US);
            if (low.startsWith("content-length:")) {
                try { length = Integer.parseInt(header.substring(15).trim()); }
                catch (NumberFormatException e) { length = -1; }
            }
            if (low.startsWith("content-type:")) contentType = header.substring(13).trim().toLowerCase(Locale.US);
        }
        String bearer = "token=" + token;
        if (!uri.contains(bearer)) { respond(out, 403, "Acesso negado"); return; }
        if (method.equals("GET") && uri.startsWith("/?")) {
            String html = "<!DOCTYPE html><html lang='pt-BR'><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<meta charset='UTF-8'><title>TCL Nova</title><style>body{background:#15101b;color:white;font:18px sans-serif;"
                + "margin:38px auto;padding:16px;max-width:440px}button{border:0;background:#b51740;color:white;border-radius:12px;"
                + "padding:16px;font-size:17px}input{margin:25px 0}p{opacity:.8}</style></head><body>"
                + "<h2>Enviar papel de parede</h2><p>Selecione uma fotografia JPG ou PNG (até 6 MB). "
                + "A TV pedirá confirmação antes de aplicar.</p><input id='pic' type='file' accept='image/jpeg,image/png'>"
                + "<br><button onclick='send()'>Enviar para a TV</button><p id='status'></p><script>async function send(){"
                + "let f=document.getElementById('pic').files[0],s=document.getElementById('status');"
                + "if(!f){s.textContent='Escolha uma foto';return;}"
                + "if(f.size>6291456){s.textContent='Arquivo acima de 6 MB';return;}"
                + "s.textContent='Enviando...';try{let r=await fetch('/upload'+location.search,{method:'POST',"
                + "headers:{'Content-Type':f.type},body:f});s.textContent=await r.text();}"
                + "catch(e){s.textContent='Falha. Verifique a conexão Wi-Fi.'}}</script></body></html>";
            respond(out, 200, html, "text/html; charset=utf-8");
            return;
        }
        if (!method.equals("POST") || !uri.startsWith("/upload?")) {
            respond(out, 404, "Não encontrado"); return;
        }
        if (delivered.get()) { respond(out, 409, "Transferência concluída"); return; }
        if (length <= 0 || length > MAX_INPUT_BYTES) {
            respond(out, 413, "Imagem excede 6 MB ou tamanho inválido"); return;
        }
        if (!contentType.startsWith("image/jpeg") && !contentType.startsWith("image/png")) {
            respond(out, 415, "Envie JPG ou PNG"); return;
        }
        File source = new File(folder, "nova_upload_raw.tmp");
        File ready = new File(folder, "nova_upload_ready.jpg");
        try {
            try (FileOutputStream write = new FileOutputStream(source)) {
                byte[] block = new byte[8192];
                int left = length;
                while (left > 0) {
                    int n = in.read(block, 0, Math.min(block.length, left));
                    if (n < 0) throw new IOException("Upload incompleto");
                    write.write(block, 0, n);
                    left -= n;
                }
            }
            if (!looksLikeImage(source, contentType)) {
                respond(out, 415, "Arquivo de imagem inválido"); return;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                bounds.outWidth > 12000 || bounds.outHeight > 12000) {
                respond(out, 415, "Dimensões inválidas"); return;
            }
            int sample = 1;
            while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080)
                sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), opts);
            if (bitmap == null) { respond(out, 415, "Não foi possível abrir a imagem"); return; }
            try (FileOutputStream write = new FileOutputStream(ready)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, write))
                    throw new IOException("Erro de conversão");
            } finally { bitmap.recycle(); }
            delivered.set(true);
            respond(out, 200, "Foto recebida! Confirme na tela da TV.");
            listener.ready(ready);
        } catch (IOException error) {
            respond(out, 500, "Não foi possível processar a foto");
        } finally {
            source.delete();
        }
    }

    private boolean looksLikeImage(File file, String type) throws IOException {
        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] head = new byte[8];
            if (in.read(head) != 8) return false;
            boolean jpg = (head[0] & 255) == 0xff && (head[1] & 255) == 0xd8;
            boolean png = (head[0] & 255) == 137 && (head[1] & 255) == 80 &&
                (head[2] & 255) == 78 && (head[3] & 255) == 71;
            return type.startsWith("image/jpeg") ? jpg : png;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int byteValue;
        while ((byteValue = in.read()) != -1) {
            if (byteValue == '\n') break;
            if (out.size() > 8192) throw new IOException("Linha HTTP longa demais");
            if (byteValue != '\r') out.write(byteValue);
        }
        if (byteValue == -1 && out.size() == 0) return null;
        return out.toString("US-ASCII");
    }

    private static void respond(OutputStream out, int status, String message) throws IOException {
        respond(out, status, message, "text/plain; charset=utf-8");
    }

    private static void respond(OutputStream out, int status, String message, String type) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        String line = status == 200 ? "OK" : "Error";
        String headers = "HTTP/1.1 " + status + " " + line + "\r\n"
            + "Content-Type: " + type + "\r\nContent-Length: " + bytes.length + "\r\n"
            + "Cache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface n : Collections.list(list)) {
                if (!n.isUp() || n.isLoopback()) continue;
                // Interfaces locais Android mais comuns; evitar mobile e VPN quando possível.
                String name = n.getName().toLowerCase(Locale.US);
                if (!name.startsWith("wlan") && !name.startsWith("eth")) continue;
                for (InetAddress addr : Collections.list(n.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress())
                        return addr.getHostAddress();
                }
            }
        } catch (SocketException ignored) { }
        return null;
    }
}
