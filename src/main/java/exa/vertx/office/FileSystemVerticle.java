package exa.vertx.office;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;





public class FileSystemVerticle extends AbstractVerticle {
    public static final String CONFIG_UPLOAD_QUEUE = "epimUpload.queue";
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        vertx.eventBus().consumer(config().getString(CONFIG_UPLOAD_QUEUE, "epimUpload.queue"), this::onMessage);
        startFuture.complete();
    }

    public void onMessage(Message<JsonObject> message) {

        if (!message.headers().contains("upload")) {
            LOGGER.error("No action header specified for message with headers {} and body {}",
                    message.headers(), message.body().encodePrettily());
            message.fail(FileSystemVerticle.ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }
        String action = message.headers().get("upload");

        switch (action) {
            case "upload-photos":
                uploadPhotos(message);
                break;
            default:
                message.fail(FileSystemVerticle.ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }

    private void uploadPhotos(Message<JsonObject> message) {
        LOGGER.info("Upload Photos");
        message.reply(new JsonObject().put("succeed","true").put("message","file has been upoad to server"));
    }

    // tag::onMessage[]
    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private void reportQueryError(Message<JsonObject> message, Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(EofficeDBVerticle.ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }


}
