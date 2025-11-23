package com.huskyapply.gateway.service.messaging;

import com.huskyapply.gateway.service.messaging.MessageCompressionService.CompressedMessage;
import com.huskyapply.gateway.service.messaging.MessageCompressionService.CompressionAlgorithm;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.AbstractMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;

/**
 * Custom message converter that handles Protocol Buffers serialization with optional compression.
 * Falls back to JSON serialization for objects that don't support protobuf.
 */
public class ProtobufMessageConverter extends AbstractMessageConverter {

  private static final Logger logger = LoggerFactory.getLogger(ProtobufMessageConverter.class);

  private static final String CONTENT_TYPE_PROTOBUF = "application/x-protobuf";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String COMPRESSION_HEADER = "x-compression";
  private static final String ORIGINAL_SIZE_HEADER = "x-original-size";

  private final MessageCompressionService compressionService;
  private boolean compressionEnabled = true;
  private int compressionThreshold = 1024; // 1KB default threshold

  public ProtobufMessageConverter(MessageCompressionService compressionService) {
    this.compressionService = compressionService;
  }

  public void setCompressionEnabled(boolean compressionEnabled) {
    this.compressionEnabled = compressionEnabled;
  }

  public void setCompressionThreshold(int compressionThreshold) {
    this.compressionThreshold = compressionThreshold;
  }

  @Override
  protected Message createMessage(Object object, MessageProperties messageProperties) {
    try {
      byte[] bytes = serializeObject(object);

      // Apply compression if enabled and data exceeds threshold
      if (compressionEnabled && bytes.length > compressionThreshold) {
        CompressedMessage compressed = compressionService.compress(bytes);
        bytes = compressed.getData();

        // Add compression metadata to message headers
        messageProperties.getHeaders().put(COMPRESSION_HEADER, compressed.getAlgorithm().name());
        messageProperties.getHeaders().put(ORIGINAL_SIZE_HEADER, compressed.getOriginalSize());

        logger.debug(
            "Compressed message from {} bytes to {} bytes using {}",
            compressed.getOriginalSize(),
            compressed.getCompressedSize(),
            compressed.getAlgorithm());
      }

      return new Message(bytes, messageProperties);

    } catch (Exception e) {
      logger.error("Failed to create message for object: {}", object.getClass().getSimpleName(), e);
      throw new MessageConversionException("Could not convert object to message", e);
    }
  }

  @Override
  public Object fromMessage(Message message) throws MessageConversionException {
    try {
      byte[] bytes = message.getBody();
      MessageProperties properties = message.getMessageProperties();

      // Check if message is compressed
      String compressionAlgorithm = (String) properties.getHeaders().get(COMPRESSION_HEADER);
      if (compressionAlgorithm != null) {
        CompressionAlgorithm algorithm = CompressionAlgorithm.valueOf(compressionAlgorithm);
        bytes = compressionService.decompress(bytes, algorithm);

        Integer originalSize = (Integer) properties.getHeaders().get(ORIGINAL_SIZE_HEADER);
        logger.debug(
            "Decompressed message from {} bytes to {} bytes using {}",
            message.getBody().length,
            originalSize != null ? originalSize : bytes.length,
            algorithm);
      }

      return deserializeObject(bytes, properties);

    } catch (Exception e) {
      logger.error("Failed to convert message to object", e);
      throw new MessageConversionException("Could not convert message to object", e);
    }
  }

  /**
   * Serialize object to bytes. Currently uses JSON fallback since protobuf requires pre-compiled
   * message classes.
   */
  private byte[] serializeObject(Object object) throws IOException {
    if (object == null) {
      return new byte[0];
    }

    // For now, use JSON serialization as fallback
    // In a real implementation, this would check if object implements Message
    // and use protobuf serialization accordingly
    String json = convertObjectToJson(object);
    return json.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Deserialize bytes to object. Currently uses JSON fallback since protobuf requires pre-compiled
   * message classes.
   */
  private Object deserializeObject(byte[] bytes, MessageProperties properties) throws IOException {
    if (bytes.length == 0) {
      return null;
    }

    String contentType = properties.getContentType();
    if (CONTENT_TYPE_PROTOBUF.equals(contentType)) {
      // In a real implementation, this would use protobuf deserialization
      // based on the message type header
      logger.debug("Protobuf deserialization not yet implemented, falling back to JSON");
    }

    // Fallback to JSON deserialization
    String json = new String(bytes, StandardCharsets.UTF_8);
    return convertJsonToObject(json);
  }

  /**
   * Simple JSON serialization fallback. In production, this would use a proper JSON library like
   * Jackson or Gson.
   */
  private String convertObjectToJson(Object object) {
    // Simplified JSON conversion for basic objects
    if (object instanceof String) {
      return "\"" + object + "\"";
    }

    // For complex objects, this would use Jackson ObjectMapper
    return object.toString();
  }

  /**
   * Simple JSON deserialization fallback. In production, this would use a proper JSON library like
   * Jackson or Gson.
   */
  private Object convertJsonToObject(String json) {
    // Simplified JSON parsing for basic strings
    if (json.startsWith("\"") && json.endsWith("\"")) {
      return json.substring(1, json.length() - 1);
    }

    // For complex objects, this would use Jackson ObjectMapper
    return json;
  }
}
