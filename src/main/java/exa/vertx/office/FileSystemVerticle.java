package exa.vertx.office;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;


public class FileSystemVerticle extends AbstractVerticle {
    public static final String PUBLIC_UPLOAD_LAPORAN="assets.laporan";
    public static final String CONFIG_UPLOAD_QUEUE = "epimUpload.queue";
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemVerticle.class);
    private String fileStore;


    @Override
    public void start(Future<Void> startFuture) throws Exception {

        vertx.eventBus().consumer(config().getString(CONFIG_UPLOAD_QUEUE, "epimUpload.queue"), this::onMessage);
        startFuture.complete();

        fileStore= config().getJsonObject("upload").getString(PUBLIC_UPLOAD_LAPORAN, "assets.laporan");
    }

    public void onMessage(Message<JsonObject> message) {
        LOGGER.info("upload");

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
        LOGGER.info("Upload Photos to create files");
        JsonObject jsonData = message.body().getJsonObject("data");

       // LOGGER.info(jsonData);

        String unor = jsonData.getString("org");
        String pegaNip = jsonData.getString("pegaNip");

        JsonArray evidenceList= null;

        boolean isCamera = jsonData.getBoolean("isCamera");

        Date date = new Date();
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        int year  = localDate.getYear();
        int month = localDate.getMonthValue();
        int day   = localDate.getDayOfMonth();
        List <String> fileNameList = new ArrayList<>();
        LOGGER.info("isCamera: "+isCamera);

        if (isCamera){
            String imageCameraImg64 = jsonData.getJsonObject("evidence").getString("baseImg64");
            String random = randomAlphaNumeric(8);
            String fileName = unor+pegaNip+year+month+day+"_"+random+".jpg";
            String filePath = fileStore+"/"+fileName;
            File file = new File(filePath);
            fileNameList.add(fileName);
            // use blocking handler for handle blocking process
            vertx.<String>executeBlocking(future -> {
                // decode base64 encoded image

                try (FileOutputStream imageOutFile = new FileOutputStream(file)) {
                    if (!file.exists()){
                        file.createNewFile();
                    }
                    // Converting a Base64 String into Image byte array
                    //byte[] imageByteArray = Base64.getDecoder().decode(imgBase64);
                    byte[] imageByteArray = Base64.getMimeDecoder().decode(imageCameraImg64);
                    imageOutFile.write(imageByteArray);

                } catch (FileNotFoundException e) {
                    LOGGER.info("Image not found "+e);
                } catch (IOException ioe) {
                    LOGGER.info("Exception while reading the Image " + ioe);
                }

                String result="succeed";


                future.complete(result);

            }, res -> {

                if (res.succeeded()) {

                    LOGGER.info("Upload photo successfull");

                } else {
                    res.failed();
                }
            });

        }else{
            evidenceList = jsonData.getJsonArray("evidence");


            // let's loop to send file to static folder
            evidenceList.forEach(row -> {
                JsonObject uri = new JsonObject(row.toString());
                String imgBase64 = uri.getString("baseImg64");
                LOGGER.info("imagebase64: "+imgBase64);

                String random = randomAlphaNumeric(8);

                String fileName = unor+pegaNip+year+month+day+"_"+random+".jpg";
                String filePath = fileStore+"/"+fileName;
                File file = new File(filePath);

                fileNameList.add(fileName);


                // use blocking handler for handle blocking process
                vertx.<String>executeBlocking(future -> {

              /*  File fileDirectory = new File(fileStore+pegaNip);

                if (!fileDirectory.exists()){
                    fileDirectory.mkdir();
                    LOGGER.info("directory created");
                }*/

                    // decode base64 encoded image

                    try (FileOutputStream imageOutFile = new FileOutputStream(file)) {
                        if (!file.exists()){
                            file.createNewFile();
                        }
                        // Converting a Base64 String into Image byte array
                        //byte[] imageByteArray = Base64.getDecoder().decode(imgBase64);
                        byte[] imageByteArray = Base64.getMimeDecoder().decode(imgBase64);
                        imageOutFile.write(imageByteArray);

                    } catch (FileNotFoundException e) {
                        LOGGER.info("Image not found "+e);
                    } catch (IOException ioe) {
                        LOGGER.info("Exception while reading the Image " + ioe);
                    }

                    String result="succeed";


                    future.complete(result);

                }, res -> {

                    if (res.succeeded()) {
                        message.reply(new JsonObject().put("succeed","true").put("message","file has been upoad to server").put("listFilename",fileNameList));

                        LOGGER.info("Upload photo successfull");


                    } else {
                        res.failed();
                        //context.fail(reply.cause());
                    }
                });

            });
        }


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

    private String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }


}
