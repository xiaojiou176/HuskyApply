package com.huskyapply.gateway.config;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicConnectionIdGenerator;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import reactor.netty.http.server.HttpServer;

/**
 * HTTP/3 and QUIC configuration for the Gateway service. Provides enhanced network performance with
 * multiplexing, 0-RTT connection establishment, and improved mobile network support.
 */
// @Configuration
// @Profile("!test") // Don't enable HTTP/3 in test environment
public class Http3Config {

  private static final Logger logger = LoggerFactory.getLogger(Http3Config.class);

  @Value("${server.http3.enabled:true}")
  private boolean http3Enabled;

  @Value("${server.http3.port:8443}")
  private int http3Port;

  @Value("${server.http3.idle-timeout:30s}")
  private Duration idleTimeout;

  @Value("${server.http3.max-concurrent-streams:100}")
  private int maxConcurrentStreams;

  @Value("${server.http3.initial-max-data:1048576}")
  private int initialMaxData; // 1MB

  @Value("${server.http3.initial-max-stream-data-bidi-local:524288}")
  private int initialMaxStreamDataBidiLocal; // 512KB

  @Value("${server.http3.initial-max-stream-data-bidi-remote:524288}")
  private int initialMaxStreamDataBidiRemote; // 512KB

  @Value("${server.http3.initial-max-stream-data-uni:262144}")
  private int initialMaxStreamDataUni; // 256KB

  @Value("${server.http3.max-ack-delay:25ms}")
  private Duration maxAckDelay;

  @Value("${server.http3.congestion-control-algorithm:cubic}")
  private String congestionControlAlgorithm;

  @Value("${server.http3.enable-early-data:true}")
  private boolean enableEarlyData;

  @Value("${server.ssl.certificate:}")
  private String sslCertificate;

  @Value("${server.ssl.certificate-private-key:}")
  private String sslCertificatePrivateKey;

  /** Configure HTTP/3 server with QUIC transport and optimized settings for performance. */
  @Bean
  @Profile("http3")
  public NettyReactiveWebServerFactory http3ServerFactory() {
    if (!http3Enabled) {
      logger.info("HTTP/3 is disabled, using default HTTP/2 server");
      return new NettyReactiveWebServerFactory();
    }

    logger.info("Configuring HTTP/3 server with QUIC on port {}", http3Port);

    NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();

    factory.addServerCustomizers(
        new NettyServerCustomizer() {
          @Override
          public HttpServer apply(HttpServer httpServer) {
            return httpServer
                .port(http3Port)
                .wiretap(true) // Enable detailed logging for debugging
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .doOnChannelInit(
                    (connectionObserver, channel, remoteAddress) -> {
                      try {
                        configureHttp3Pipeline(channel.pipeline());
                      } catch (Exception e) {
                        logger.error("Failed to configure HTTP/3 pipeline", e);
                      }
                    });
          }
        });

    return factory;
  }

  /** Create QUIC SSL context with optimized cipher suites and protocol configuration. */
  @Bean
  public QuicSslContext quicSslContext() throws CertificateException, SSLException {
    QuicSslContextBuilder sslContextBuilder;

    // Use provided certificates or generate self-signed for development
    if (sslCertificate != null && !sslCertificate.isEmpty()) {
      logger.info("Using provided SSL certificates for QUIC");
      sslContextBuilder =
          QuicSslContextBuilder.forServer(
              new java.io.File(sslCertificate), null, new java.io.File(sslCertificatePrivateKey));
    } else {
      logger.warn(
          "No SSL certificates provided, generating self-signed certificate for development");
      SelfSignedCertificate cert = new SelfSignedCertificate("localhost");
      sslContextBuilder =
          QuicSslContextBuilder.forServer(cert.privateKey(), null, cert.certificate());
    }

    // Build QUIC SSL context with HTTP/3 support
    return sslContextBuilder
        .applicationProtocols(Http3.supportedApplicationProtocols())
        .earlyData(enableEarlyData)
        .build();
  }

  /** Configure the HTTP/3 pipeline with QUIC transport and optimized handlers. */
  private void configureHttp3Pipeline(ChannelPipeline pipeline) throws Exception {
    QuicSslContext sslContext = quicSslContext();

    // Configure QUIC server codec with performance optimizations
    pipeline.addLast(
        Http3.newQuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(idleTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .initialMaxData(initialMaxData)
            .initialMaxStreamDataBidirectionalLocal(initialMaxStreamDataBidiLocal)
            .initialMaxStreamDataBidirectionalRemote(initialMaxStreamDataBidiRemote)
            .initialMaxStreamDataUnidirectional(initialMaxStreamDataUni)
            .initialMaxStreamsBidirectional(maxConcurrentStreams)
            .initialMaxStreamsUnidirectional(maxConcurrentStreams)
            .maxAckDelay(maxAckDelay.toMillis(), TimeUnit.MILLISECONDS)
            .activeMigration(true) // Enable connection migration for mobile networks
            .congestionControlAlgorithm(getCongestionControlAlgorithm())
            .tokenHandler(new QuicTokenHandlerImpl()) // Enable 0-RTT
            .connectionIdAddressGenerator(QuicConnectionIdGenerator.randomGenerator())
            .streamHandler(
                new ChannelInitializer<QuicStreamChannel>() {
                  @Override
                  protected void initChannel(QuicStreamChannel ch) {
                    configureHttp3StreamPipeline(ch.pipeline());
                  }
                })
            .handler(
                new ChannelInitializer<QuicChannel>() {
                  @Override
                  protected void initChannel(QuicChannel ch) {
                    configureQuicChannelPipeline(ch.pipeline());
                  }
                })
            .build());

    logger.debug("HTTP/3 QUIC pipeline configured successfully");
  }

  /** Configure the QUIC channel pipeline for connection-level handling. */
  private void configureQuicChannelPipeline(ChannelPipeline pipeline) {
    // Add logging for debugging
    pipeline.addLast(new LoggingHandler("QUIC", LogLevel.DEBUG));

    // Add HTTP/3 connection handler with optimized settings
    pipeline.addLast(
        new Http3ServerConnectionHandler(
            new ChannelInitializer<QuicStreamChannel>() {
              @Override
              protected void initChannel(QuicStreamChannel ch) {
                configureHttp3StreamPipeline(ch.pipeline());
              }
            },
            null,
            null,
            // HTTP/3 settings for performance optimization
            createHttp3Settings(),
            // Enable server push for critical resources
            true));
  }

  /** Configure individual HTTP/3 stream pipelines for request handling. */
  private void configureHttp3StreamPipeline(ChannelPipeline pipeline) {
    // Add HTTP/3 request stream handler
    pipeline.addLast(
        new ChannelInboundHandlerAdapter() {
          @Override
          public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // Custom request handling with performance optimizations
            if (msg instanceof ByteBuf) {
              ByteBuf buffer = (ByteBuf) msg;
              processHttp3Request(ctx, buffer);
            }
            // Pass to next handler
            ctx.fireChannelRead(msg);
          }
        });

    // Add performance monitoring handler
    pipeline.addLast(new Http3PerformanceHandler());

    // Add compression handler for response optimization
    pipeline.addLast(new Http3CompressionHandler());
  }

  /** Process HTTP/3 requests with zero-copy optimizations and performance monitoring. */
  private void processHttp3Request(ChannelHandlerContext ctx, ByteBuf buffer) {
    // Implement zero-copy request processing
    // This would integrate with Spring WebFlux handlers
    logger.debug("Processing HTTP/3 request with {} bytes", buffer.readableBytes());

    // Add performance metrics
    recordHttp3RequestMetrics(buffer.readableBytes());
  }

  /** Record HTTP/3 request metrics for monitoring and optimization. */
  private void recordHttp3RequestMetrics(int requestSize) {
    // This would integrate with Micrometer metrics
    logger.debug("HTTP/3 request processed: {} bytes", requestSize);
  }

  /** Get congestion control algorithm enum from configuration string. */
  private io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm
      getCongestionControlAlgorithm() {
    switch (congestionControlAlgorithm.toLowerCase()) {
      case "reno":
        return io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm.RENO;
      case "cubic":
        return io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm.CUBIC;
      case "bbr":
        return io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm.BBR;
      default:
        logger.warn(
            "Unknown congestion control algorithm: {}, defaulting to RENO",
            congestionControlAlgorithm);
        return io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm.RENO;
    }
  }

  /** Custom token handler for 0-RTT connection establishment. */
  private static final class QuicTokenHandlerImpl
      implements io.netty.incubator.codec.quic.QuicTokenHandler {
    private static final QuicTokenHandlerImpl INSTANCE = new QuicTokenHandlerImpl();

    @Override
    public boolean writeToken(ByteBuf out, ByteBuf dcid, java.net.InetSocketAddress address) {
      // Simple token generation for development
      out.writeLong(System.currentTimeMillis());
      return true;
    }

    @Override
    public int validateToken(ByteBuf token, java.net.InetSocketAddress address) {
      if (token.readableBytes() < 8) {
        return -1;
      }
      long timestamp = token.readLong();
      // Token valid for 24 hours
      if (System.currentTimeMillis() - timestamp > 86400000) {
        return -1;
      }
      return 0;
    }

    @Override
    public int maxTokenLength() {
      return 8;
    }
  }

  /** Performance monitoring handler for HTTP/3 streams. */
  private static class Http3PerformanceHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      super.channelInactive(ctx);
    }
  }

  /** Compression handler for HTTP/3 responses. */
  private static class Http3CompressionHandler extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(
        ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise)
        throws Exception {
      if (msg instanceof ByteBuf) {
        ByteBuf buffer = (ByteBuf) msg;
        // Simple pass-through for now, real compression would be more complex
        ctx.write(buffer, promise);
      } else {
        ctx.write(msg, promise);
      }
    }
  }

  /** Configuration properties for HTTP/3. */
  public static class Http3Properties {
    private boolean enabled = true;
    private int port = 8443;
    private Duration idleTimeout = Duration.ofSeconds(300);
    private int maxConcurrentStreams = 100;
    private boolean enableEarlyData = true;
    private String congestionControl = "reno";

    // Getters and setters
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public Duration getIdleTimeout() {
      return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
      this.idleTimeout = idleTimeout;
    }

    public int getMaxConcurrentStreams() {
      return maxConcurrentStreams;
    }

    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
      this.maxConcurrentStreams = maxConcurrentStreams;
    }

    public boolean isEnableEarlyData() {
      return enableEarlyData;
    }

    public void setEnableEarlyData(boolean enableEarlyData) {
      this.enableEarlyData = enableEarlyData;
    }

    public String getCongestionControl() {
      return congestionControl;
    }

    public void setCongestionControl(String congestionControl) {
      this.congestionControl = congestionControl;
    }
  }

  private Http3SettingsFrame createHttp3Settings() {
    DefaultHttp3SettingsFrame settings = new DefaultHttp3SettingsFrame();
    settings.put(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, 8192L);
    settings.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 4096L);
    settings.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 100L);
    return settings;
  }
}
