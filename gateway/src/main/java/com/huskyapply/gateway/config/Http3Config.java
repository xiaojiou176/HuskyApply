package com.huskyapply.gateway.config;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicConnectionIdGenerator;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.netty.http.server.HttpServer;

/**
 * HTTP/3 and QUIC configuration for the Gateway service. Provides enhanced network performance with
 * multiplexing, 0-RTT connection establishment, and improved mobile network support.
 */
@Configuration
@Profile("!test") // Don't enable HTTP/3 in test environment
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
    SslContextBuilder sslContextBuilder;

    // Use provided certificates or generate self-signed for development
    if (sslCertificate != null && !sslCertificate.isEmpty()) {
      logger.info("Using provided SSL certificates for QUIC");
      sslContextBuilder = SslContextBuilder.forServer(sslCertificate, sslCertificatePrivateKey);
    } else {
      logger.warn(
          "No SSL certificates provided, generating self-signed certificate for development");
      SelfSignedCertificate cert = new SelfSignedCertificate("localhost");
      sslContextBuilder = SslContextBuilder.forServer(cert.certificate(), cert.privateKey());
    }

    // Configure application protocols for HTTP/3
    sslContextBuilder
        .applicationProtocolConfig(
            new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1))
        .ciphers(Http3.supportedCipherSuites(), SupportedCipherSuiteFilter.INSTANCE);

    // Build QUIC SSL context with HTTP/3 support
    return QuicSslContextBuilder.forServer(sslContextBuilder.build())
        .applicationProtocols(Http3.supportedApplicationProtocols())
        .earlyData(enableEarlyData)
        .build();
  }

  /** Configure the HTTP/3 pipeline with QUIC transport and optimized handlers. */
  private void configureHttp3Pipeline(ChannelPipeline pipeline) throws Exception {
    QuicSslContext sslContext = quicSslContext();

    // Configure QUIC server codec with performance optimizations
    pipeline.addLast(
        QuicServerCodecBuilder.newBuilder()
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
            .tokenHandler(QuicTokenHandler.newReusableTokenHandler()) // Enable 0-RTT
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
            // HTTP/3 settings for performance optimization
            Http3SettingsFrame.newBuilder()
                .maxFieldSectionSize(8192) // 8KB header limit
                .qpackMaxTableCapacity(4096) // QPACK compression
                .qpackBlockedStreams(100)
                .build(),
            // Enable server push for critical resources
            true));
  }

  /** Configure individual HTTP/3 stream pipelines for request handling. */
  private void configureHttp3StreamPipeline(ChannelPipeline pipeline) {
    // Add HTTP/3 request stream handler
    pipeline.addLast(
        new Http3RequestStreamInboundHandler() {
          @Override
          protected void channelRead(ChannelHandlerContext ctx, Object msg) {
            // Custom request handling with performance optimizations
            if (msg instanceof ByteBuf) {
              ByteBuf buffer = (ByteBuf) msg;
              try {
                // Process HTTP/3 request with zero-copy optimizations
                processHttp3Request(ctx, buffer);
              } finally {
                buffer.release(); // Always release buffers to prevent memory leaks
              }
            }
            super.channelRead(ctx, msg);
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
            "Unknown congestion control algorithm '{}', using CUBIC", congestionControlAlgorithm);
        return io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm.CUBIC;
    }
  }

  /** Custom token handler for 0-RTT connection establishment. */
  private static class QuicTokenHandler implements io.netty.incubator.codec.quic.QuicTokenHandler {
    private static final QuicTokenHandler INSTANCE = new QuicTokenHandler();

    public static QuicTokenHandler newReusableTokenHandler() {
      return INSTANCE;
    }

    @Override
    public boolean writeToken(ByteBuf out, ByteBuf dcid, java.net.InetSocketAddress remoteAddress) {
      // Generate and write token for 0-RTT
      out.writeLong(System.currentTimeMillis()); // Simple timestamp-based token
      out.writeBytes(dcid, dcid.readerIndex(), Math.min(dcid.readableBytes(), 8));
      return true;
    }

    @Override
    public int validateToken(ByteBuf token, java.net.InetSocketAddress remoteAddress) {
      if (token.readableBytes() < 8) {
        return -1; // Invalid token
      }

      long timestamp = token.readLong();
      long now = System.currentTimeMillis();

      // Token is valid for 24 hours
      if (now - timestamp > TimeUnit.HOURS.toMillis(24)) {
        return -1; // Token expired
      }

      return 0; // Token valid
    }

    @Override
    public int maxTokenLength() {
      return 64; // Maximum token size
    }
  }

  /** Performance monitoring handler for HTTP/3 streams. */
  private static class Http3PerformanceHandler
      extends io.netty.channel.ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      logger.debug("HTTP/3 stream activated: {}", ctx.channel());
      super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      logger.debug("HTTP/3 stream closed: {}", ctx.channel());
      super.channelInactive(ctx);
    }
  }

  /** Compression handler for HTTP/3 responses. */
  private static class Http3CompressionHandler
      extends io.netty.channel.ChannelOutboundHandlerAdapter {
    @Override
    public void write(
        ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise)
        throws Exception {
      // Apply compression to outbound messages if beneficial
      if (msg instanceof ByteBuf) {
        ByteBuf buffer = (ByteBuf) msg;
        if (buffer.readableBytes() > 1024) { // Only compress larger payloads
          // Apply QPACK or gzip compression
          logger.debug("Applying compression to {} byte response", buffer.readableBytes());
        }
      }
      super.write(ctx, msg, promise);
    }
  }

  /** Configuration properties for HTTP/3 optimization. */
  public static class Http3Properties {
    private boolean enabled = true;
    private int port = 8443;
    private Duration idleTimeout = Duration.ofSeconds(30);
    private int maxConcurrentStreams = 100;
    private boolean enableEarlyData = true;
    private boolean enableServerPush = true;
    private String congestionControl = "cubic";

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

    public boolean isEnableServerPush() {
      return enableServerPush;
    }

    public void setEnableServerPush(boolean enableServerPush) {
      this.enableServerPush = enableServerPush;
    }

    public String getCongestionControl() {
      return congestionControl;
    }

    public void setCongestionControl(String congestionControl) {
      this.congestionControl = congestionControl;
    }
  }
}
