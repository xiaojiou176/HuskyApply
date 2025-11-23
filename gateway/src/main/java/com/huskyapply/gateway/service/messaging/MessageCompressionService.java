package com.huskyapply.gateway.service.messaging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xerial.snappy.Snappy;

/**
 * Service for compressing and decompressing message payloads using various algorithms. Supports
 * GZIP, LZ4, and Snappy compression for optimal message size reduction.
 */
@Service
public class MessageCompressionService {

  private static final Logger logger = LoggerFactory.getLogger(MessageCompressionService.class);

  @Value("${rabbitmq.compression.algorithm:LZ4}")
  private String defaultCompressionAlgorithm;

  @Value("${rabbitmq.compression.level:6}")
  private int compressionLevel;

  private final LZ4Factory lz4Factory;
  private final LZ4Compressor lz4Compressor;
  private final LZ4SafeDecompressor lz4Decompressor;

  public MessageCompressionService() {
    this.lz4Factory = LZ4Factory.fastestInstance();
    this.lz4Compressor = lz4Factory.fastCompressor();
    this.lz4Decompressor = lz4Factory.safeDecompressor();
  }

  /** Compression algorithms supported by the service. */
  public enum CompressionAlgorithm {
    NONE,
    GZIP,
    LZ4,
    SNAPPY
  }

  /**
   * Compress data using the default algorithm.
   *
   * @param data the data to compress
   * @return compressed data with algorithm metadata
   * @throws IOException if compression fails
   */
  public CompressedMessage compress(byte[] data) throws IOException {
    return compress(data, CompressionAlgorithm.valueOf(defaultCompressionAlgorithm.toUpperCase()));
  }

  /**
   * Compress data using the specified algorithm.
   *
   * @param data the data to compress
   * @param algorithm the compression algorithm to use
   * @return compressed data with algorithm metadata
   * @throws IOException if compression fails
   */
  public CompressedMessage compress(byte[] data, CompressionAlgorithm algorithm)
      throws IOException {
    if (data == null || data.length == 0) {
      return new CompressedMessage(data, CompressionAlgorithm.NONE, data.length, data.length);
    }

    long startTime = System.nanoTime();
    byte[] compressedData;
    int originalSize = data.length;

    try {
      switch (algorithm) {
        case GZIP:
          compressedData = compressGzip(data);
          break;
        case LZ4:
          compressedData = compressLz4(data);
          break;
        case SNAPPY:
          compressedData = compressSnappy(data);
          break;
        case NONE:
        default:
          compressedData = data;
          break;
      }

      long compressionTime = System.nanoTime() - startTime;
      double compressionRatio =
          originalSize > 0 ? (double) compressedData.length / originalSize : 1.0;

      logger.debug(
          "Compressed {} bytes to {} bytes using {} in {}μs (ratio: {:.2f})",
          originalSize,
          compressedData.length,
          algorithm,
          compressionTime / 1000,
          compressionRatio);

      return new CompressedMessage(compressedData, algorithm, originalSize, compressedData.length);

    } catch (Exception e) {
      logger.error("Failed to compress data using {}: {}", algorithm, e.getMessage());
      throw new IOException("Compression failed", e);
    }
  }

  /**
   * Decompress data using the algorithm specified in the compressed message.
   *
   * @param compressedMessage the compressed message to decompress
   * @return decompressed data
   * @throws IOException if decompression fails
   */
  public byte[] decompress(CompressedMessage compressedMessage) throws IOException {
    return decompress(compressedMessage.getData(), compressedMessage.getAlgorithm());
  }

  /**
   * Decompress data using the specified algorithm.
   *
   * @param compressedData the compressed data
   * @param algorithm the compression algorithm used
   * @return decompressed data
   * @throws IOException if decompression fails
   */
  public byte[] decompress(byte[] compressedData, CompressionAlgorithm algorithm)
      throws IOException {
    if (compressedData == null || compressedData.length == 0) {
      return compressedData;
    }

    long startTime = System.nanoTime();
    byte[] decompressedData;

    try {
      switch (algorithm) {
        case GZIP:
          decompressedData = decompressGzip(compressedData);
          break;
        case LZ4:
          decompressedData = decompressLz4(compressedData);
          break;
        case SNAPPY:
          decompressedData = decompressSnappy(compressedData);
          break;
        case NONE:
        default:
          decompressedData = compressedData;
          break;
      }

      long decompressionTime = System.nanoTime() - startTime;

      logger.debug(
          "Decompressed {} bytes to {} bytes using {} in {}μs",
          compressedData.length,
          decompressedData.length,
          algorithm,
          decompressionTime / 1000);

      return decompressedData;

    } catch (Exception e) {
      logger.error("Failed to decompress data using {}: {}", algorithm, e.getMessage());
      throw new IOException("Decompression failed", e);
    }
  }

  /**
   * Calculate compression statistics for the given data and algorithm.
   *
   * @param data the data to analyze
   * @param algorithm the compression algorithm
   * @return compression statistics
   */
  public CompressionStats calculateStats(byte[] data, CompressionAlgorithm algorithm) {
    try {
      long startTime = System.nanoTime();
      CompressedMessage compressed = compress(data, algorithm);
      long compressionTime = System.nanoTime() - startTime;

      startTime = System.nanoTime();
      decompress(compressed);
      long decompressionTime = System.nanoTime() - startTime;

      return new CompressionStats(
          algorithm,
          compressed.getOriginalSize(),
          compressed.getCompressedSize(),
          compressionTime,
          decompressionTime);

    } catch (IOException e) {
      logger.warn("Failed to calculate compression stats for {}: {}", algorithm, e.getMessage());
      return new CompressionStats(algorithm, data.length, data.length, 0, 0);
    }
  }

  // Private compression methods

  private byte[] compressGzip(byte[] data) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
      gzipOut.write(data);
      gzipOut.finish();
      return baos.toByteArray();
    }
  }

  private byte[] decompressGzip(byte[] compressedData) throws IOException {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        GZIPInputStream gzipIn = new GZIPInputStream(bais);
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

      byte[] buffer = new byte[8192];
      int len;
      while ((len = gzipIn.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
      return baos.toByteArray();
    }
  }

  private byte[] compressLz4(byte[] data) {
    int maxCompressedLength = lz4Compressor.maxCompressedLength(data.length);
    byte[] compressed = new byte[maxCompressedLength + 4]; // +4 for original size

    // Store original size in first 4 bytes
    compressed[0] = (byte) (data.length >>> 24);
    compressed[1] = (byte) (data.length >>> 16);
    compressed[2] = (byte) (data.length >>> 8);
    compressed[3] = (byte) data.length;

    int compressedSize = lz4Compressor.compress(data, 0, data.length, compressed, 4);

    // Return only the used portion
    byte[] result = new byte[compressedSize + 4];
    System.arraycopy(compressed, 0, result, 0, result.length);
    return result;
  }

  private byte[] decompressLz4(byte[] compressedData) {
    // Read original size from first 4 bytes
    int originalSize =
        ((compressedData[0] & 0xFF) << 24)
            | ((compressedData[1] & 0xFF) << 16)
            | ((compressedData[2] & 0xFF) << 8)
            | (compressedData[3] & 0xFF);

    byte[] decompressed = new byte[originalSize];
    lz4Decompressor.decompress(compressedData, 4, compressedData.length - 4, decompressed, 0);
    return decompressed;
  }

  private byte[] compressSnappy(byte[] data) throws IOException {
    return Snappy.compress(data);
  }

  private byte[] decompressSnappy(byte[] compressedData) throws IOException {
    return Snappy.uncompress(compressedData);
  }

  // Data classes

  /** Compressed message with metadata. */
  public static class CompressedMessage {
    private final byte[] data;
    private final CompressionAlgorithm algorithm;
    private final int originalSize;
    private final int compressedSize;

    public CompressedMessage(
        byte[] data, CompressionAlgorithm algorithm, int originalSize, int compressedSize) {
      this.data = data;
      this.algorithm = algorithm;
      this.originalSize = originalSize;
      this.compressedSize = compressedSize;
    }

    public byte[] getData() {
      return data;
    }

    public CompressionAlgorithm getAlgorithm() {
      return algorithm;
    }

    public int getOriginalSize() {
      return originalSize;
    }

    public int getCompressedSize() {
      return compressedSize;
    }

    public double getCompressionRatio() {
      return originalSize > 0 ? (double) compressedSize / originalSize : 1.0;
    }

    public int getSavedBytes() {
      return originalSize - compressedSize;
    }
  }

  /** Compression performance statistics. */
  public static class CompressionStats {
    private final CompressionAlgorithm algorithm;
    private final int originalSize;
    private final int compressedSize;
    private final long compressionTimeNanos;
    private final long decompressionTimeNanos;

    public CompressionStats(
        CompressionAlgorithm algorithm,
        int originalSize,
        int compressedSize,
        long compressionTimeNanos,
        long decompressionTimeNanos) {
      this.algorithm = algorithm;
      this.originalSize = originalSize;
      this.compressedSize = compressedSize;
      this.compressionTimeNanos = compressionTimeNanos;
      this.decompressionTimeNanos = decompressionTimeNanos;
    }

    public CompressionAlgorithm getAlgorithm() {
      return algorithm;
    }

    public int getOriginalSize() {
      return originalSize;
    }

    public int getCompressedSize() {
      return compressedSize;
    }

    public double getCompressionRatio() {
      return originalSize > 0 ? (double) compressedSize / originalSize : 1.0;
    }

    public long getCompressionTimeNanos() {
      return compressionTimeNanos;
    }

    public long getDecompressionTimeNanos() {
      return decompressionTimeNanos;
    }

    public double getThroughputMbps() {
      long totalTimeNanos = compressionTimeNanos + decompressionTimeNanos;
      if (totalTimeNanos > 0) {
        double seconds = totalTimeNanos / 1_000_000_000.0;
        double megabytes = originalSize / 1_000_000.0;
        return megabytes / seconds;
      }
      return 0.0;
    }
  }
}
