package exa.vertx.office;


import io.github.jklingsporn.vertx.push.PushClient;
import io.vertx.core.AbstractVerticle;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;


import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;

import java.util.HashMap;

import java.util.List;
import java.util.Properties;


public class OfficeDatabaseVerticle extends AbstractVerticle {

    public static final String CONFIG_API_JDBC_URL = "api.jdbc.url";
    public static final String CONFIG_API_JDBC_DRIVER_CLASS = "api.jdbc.driver_class";
    public static final String CONFIG_API_JDBC_USER = "api.jdbc.user";
    public static final String CONFIG_API_JDBC_PASSWORD = "api.jbc.password";
    public static final String CONFIG_API_JDBC_MAX_POOL_SIZE = "api.jdbc.max_pool_size";
    public static final String CONFIG_API_SQL_QUERIES_RESOURCE_FILE = "api.sqlqueries.resource.file";


    private static String  ONESIGNAL_APP_ID="token.push.id";
    private static String  ONESIGNAL_API_KEY="token.push.key";

    public static final String CONFIG_APIDB_QUEUE = "epimdb.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(OfficeDatabaseVerticle.class);

    // (...)
    // end::preamble[]

    // tag::loadSqlQueries[]
    private enum SqlQuery {
        GET_LOGIN,
        UPDATE_PLAYER,
        GET_USER,
        GET_MAIL,
        GET_MAIL_TO,
        GET_EMP_TREE,
        GET_GRAPH_MAIL,
        GET_STAFF_NOTIF,
        GET_ATTACHMENT,
        GET_REPORT_BY_MAIL,
        GET_REPORT_BY_NIP,
        GET_DRAFT_CHAT,
        GET_SHARE_DOC,
        POST_DELETE_STAFF,
        POST_MAIL_MREQPLAY,
        POST_MAIL_ATTACH,
        /* OLD QUERY BELOW*/
        GET_INBOX,
        GET_DASHBOARD,
        POST_NOTIFIKASI,
        POST_DISPOSISI,
        POST_UPDATE_MANO,
        POST_INSERT_REPORT,
        POST_MAIL_EVIDENCE,
        UPDATE_INBOX,
        UPDATE_STATUS_INBOX,

    }


    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {



        String queriesFile = config().getString(CONFIG_API_SQL_QUERIES_RESOURCE_FILE);
        InputStream queriesInputStream;
        if (queriesFile != null) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/db-eoffice-old.properties");
        }

        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        sqlQueries.put(SqlQuery.GET_LOGIN, queriesProps.getProperty("get-user-login"));
        sqlQueries.put(SqlQuery.UPDATE_PLAYER, queriesProps.getProperty("update-user-player"));
        sqlQueries.put(SqlQuery.GET_USER, queriesProps.getProperty("get-user-profile"));


        /* new query remark with prefix mail*/
        sqlQueries.put(SqlQuery.GET_MAIL, queriesProps.getProperty("get-mail"));
        sqlQueries.put(SqlQuery.GET_MAIL_TO, queriesProps.getProperty("get-mail-to"));
        sqlQueries.put(SqlQuery.GET_EMP_TREE, queriesProps.getProperty("get-emp-tree"));
        sqlQueries.put(SqlQuery.GET_GRAPH_MAIL, queriesProps.getProperty("get-graph-mail"));
        sqlQueries.put(SqlQuery.GET_STAFF_NOTIF, queriesProps.getProperty("get-staff-notif"));
        sqlQueries.put(SqlQuery.GET_ATTACHMENT, queriesProps.getProperty("get-attachment"));
        sqlQueries.put(SqlQuery.GET_REPORT_BY_MAIL, queriesProps.getProperty("get-report-by-mail"));
        sqlQueries.put(SqlQuery.GET_REPORT_BY_NIP, queriesProps.getProperty("get-report-by-nip"));
        sqlQueries.put(SqlQuery.GET_DRAFT_CHAT, queriesProps.getProperty("get-draft-chat"));
        sqlQueries.put(SqlQuery.GET_SHARE_DOC, queriesProps.getProperty("get-share-doc"));

        sqlQueries.put(SqlQuery.POST_DELETE_STAFF, queriesProps.getProperty("post-delete-staff"));
        sqlQueries.put(SqlQuery.UPDATE_INBOX, queriesProps.getProperty("update-inbox"));
        sqlQueries.put(SqlQuery.POST_DISPOSISI, queriesProps.getProperty("post-staff"));
        sqlQueries.put(SqlQuery.POST_UPDATE_MANO, queriesProps.getProperty("post-update-mano"));
        sqlQueries.put(SqlQuery.POST_INSERT_REPORT, queriesProps.getProperty("post-mail-report"));
        sqlQueries.put(SqlQuery.POST_MAIL_MREQPLAY, queriesProps.getProperty("post-mail-reqplay"));
        sqlQueries.put(SqlQuery.POST_MAIL_ATTACH, queriesProps.getProperty("post-mail-attach"));



        //sqlQueries.put(SqlQuery.UPDATE_INBOX, queriesProps.getProperty("update-inbox"));
        sqlQueries.put(SqlQuery.UPDATE_STATUS_INBOX, queriesProps.getProperty("update-status-inbox"));


    }
    // end::loadSqlQueries[]

    // tag::start[]
    private JDBCClient dbClient;
    private String oneSignalApiId;
    private String onesignalApiKey;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        loadSqlQueries();
        //get token onesignal
        oneSignalApiId = config().getString(ONESIGNAL_APP_ID,"f95f68a6-ed64-4b75-8d9d-79716a73d5ac");
        onesignalApiKey = config().getString(ONESIGNAL_API_KEY,"MThiOTYzOTEtMjg0YS00NjhiLWJiMTMtYjlmOGJhNmJkMjJi");




        dbClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", config().getString(CONFIG_API_JDBC_URL, "jdbc:postgresql://localhost:5432/db-office-prod"))
                .put("user",config().getString(CONFIG_API_JDBC_USER,"postgres"))
                .put("password",config().getString(CONFIG_API_JDBC_PASSWORD,"admin"))
                .put("max_pool_size", config().getInteger(CONFIG_API_JDBC_MAX_POOL_SIZE, 30)));



        dbClient.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                startFuture.fail(ar.cause());
            } else {
               // LOGGER.info("connection result: "+ar.succeeded());
                vertx.eventBus().consumer(config().getString(CONFIG_APIDB_QUEUE, "epimdb.queue"), this::onMessage);
                startFuture.complete();
                LOGGER.info("Connection established");

            }
        });


    }

    public void onMessage(Message<JsonObject> message) {

        if (!message.headers().contains("action")) {
            LOGGER.error("No action header specified for message with headers {} and body {}",
                    message.headers(), message.body().encodePrettily());
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }
        String action = message.headers().get("action");

        switch (action) {
            case "get-user-login":
                getUserLogin(message);
                break;
            case "get-user":
                getUser(message);
                break;
            case "get-mail":
                getMail(message);
                break;
            case "get-mail-to":
                getMailTo(message);
                break;
            case "get-emp-tree":
                getEmpTree(message);
            break;
            case "get-graph-mail":
                getGraphMail(message);
                break;
            case "get-attachment":
                getAttachment(message);
                break;
            case "get-report-by-mail":
                getReportByMail(message);
                break;
            case "get-report-by-nip":
                getReportByNip(message);
                break;
            case "get-draft-chat":
                getDraftChat(message);
            break;
            case "get-share-doc":
            getShareDoc(message);
            break;    
            case "post-disposisi":
                postDisposisi(message);
                break;
            case "post-report":
                postReport(message);
                break;
            case "post-delete-staff":
                disposeStaff(message);
                break;
            case "post-dispos-notif":
                postDisposisi(message);
                break;
            case "post-notif":
                push(message);
                break;
            case "post-mail-mreqplay":
                postMailReqplay(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }

    private void getShareDoc(Message<JsonObject> message) {
        String share = message.body().getString("share");

        JsonArray params = new JsonArray().add(share);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_SHARE_DOC), params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("doc", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
	}

	private void postMailReqplay(Message<JsonObject> message) {
        JsonObject resJsonData = message.body().getJsonObject("data");
        LOGGER.info("postReport : "+resJsonData);

        Integer mailId = resJsonData.getInteger("mailId");
        Integer mreqId = resJsonData.getInteger("mreq");
        Integer userId = resJsonData.getInteger("userId");
        String fileName = message.body().getString("filename");
        String status = resJsonData.getString("status");
        String note =resJsonData.getString("message");


        LOGGER.info("postMailReqPlay : "+status+"/"+"/"+mailId+"/"+"/"+fileName+"/"+userId+"/"+note+"/"+mreqId);

        LocalDate localTime = LocalDate.now();
        Instant instant = Instant.now();

       JsonArray params = new JsonArray().add(mailId).add(localTime.toString()).add(note).add(userId);
        JsonArray paramsAttach = new JsonArray().add(fileName).add(mailId).add("png").add(userId).add(instant).add(mreqId);

        dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_MAIL_MREQPLAY),params,res ->{
            if (res.succeeded()){
                message.reply(new JsonObject().put("succeed","true"));
                dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_MAIL_ATTACH),paramsAttach, resUpdate ->{
                    if (resUpdate.succeeded()){
                        message.reply(new JsonObject().put("succeed","true"));
                    }else{
                        reportQueryError(message, resUpdate.cause());
                    }
                });
            }  else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getDraftChat(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");

        JsonArray params = new JsonArray().add(mailId);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_DRAFT_CHAT), params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("data", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getReportByNip(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        Integer manoId = message.body().getInteger("manoId");
        LOGGER.info("getReportByNip : "+mailId);
        LOGGER.info("MANO : "+manoId);
        JsonArray params = new JsonArray().add(mailId).add(manoId);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_REPORT_BY_NIP), params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("data", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getReportByMail(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        LOGGER.info("getReportByMail : "+mailId);

        JsonArray params = new JsonArray().add(mailId);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_REPORT_BY_MAIL), params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("report", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getAttachment(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        LOGGER.info("getAttachment : "+mailId);

        JsonArray params = new JsonArray().add(mailId);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_ATTACHMENT), params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("attachment", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void push(Message<JsonObject> message) {

        String mailId = message.body().getString("mailId");
        String mailType = message.body().getString("mailType");
        LOGGER.info("postStaffNotif : "+mailId+"/"+mailType);

        JsonArray paramNotif = new JsonArray().add(mailId);
        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_STAFF_NOTIF),paramNotif, resPush -> {
            if (resPush.succeeded()) {
                JsonArray arr = new JsonArray();

                for (JsonArray res: resPush.result().getResults()) {
                    arr.add(res.getString(0));
                }

                LOGGER.info("arr: "+arr);

                pushNotif(arr,mailType);

                message.reply(new JsonObject().put("mail", arr));


            } else {
                reportQueryError(message, resPush.cause());
            }
        });
    }

    private void postReport(Message<JsonObject> message) {


        JsonObject resJsonData = message.body().getJsonObject("data");
        LOGGER.info("postReport : "+resJsonData);

        Integer mailId = resJsonData.getInteger("mailId");
        Integer manoId = resJsonData.getInteger("manoId");
        Integer userId = resJsonData.getInteger("userId");
        String fileName = message.body().getString("filename");
        String status = resJsonData.getString("status");
        String note =resJsonData.getString("message");

        LOGGER.info("postReport : "+status+"/"+manoId+"/"+mailId+"/"+"/"+fileName+"/"+userId+"/"+note);

        LocalDate localTime = LocalDate.now();
        Instant instant = Instant.now();

        JsonArray params = new JsonArray().add(fileName).add(fileName).add(localTime.toString()).add(note).add(manoId).add(mailId).add(userId);
        JsonArray paramsUpdate = new JsonArray().add(status).add(note).add(instant).add(manoId);

        dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_INSERT_REPORT),params,res ->{
            if (res.succeeded()){
                message.reply(new JsonObject().put("succeed","true"));
                dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_UPDATE_MANO),paramsUpdate, resUpdate ->{
                    if (resUpdate.succeeded()){
                        message.reply(new JsonObject().put("succeed","true"));
                    }else{
                        reportQueryError(message, resUpdate.cause());
                    }
                });
            }  else {
                reportQueryError(message, res.cause());
            }
        });

    }

    private void getGraphMail(Message<JsonObject> message) {
        String pegaNip = message.body().getString("pegaNip");
        LOGGER.info("getGraphMail : "+pegaNip);

        JsonArray params = new JsonArray().add(pegaNip).add(pegaNip).add(pegaNip);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_GRAPH_MAIL), params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("data", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void disposeStaff(Message<JsonObject> message) {
        JsonObject jsonData = message.body().getJsonObject("data");
        Integer manoId = jsonData.getInteger("manoId");

        LOGGER.info("disposeStaff : "+manoId);

        JsonArray params = new JsonArray().add(manoId);

        dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_DELETE_STAFF),params,res ->{
            if (res.succeeded()){
                message.reply(new JsonObject().put("succeed","true"));
            }  else {
                reportQueryError(message, res.cause());
            }
        });
    }


    private void getEmpTree(Message<JsonObject> message) {


        String pegaId = message.body().getString("pegaId");

        JsonArray params = new JsonArray().add(pegaId);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_EMP_TREE), params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);

                message.reply(new JsonObject().put("emp", arr));
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getMailTo(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        JsonArray params = new JsonArray().add(mailId);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_MAIL_TO),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("mail", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getMail(Message<JsonObject> message) {

        String username = message.body().getString("username");
        String mailType  = message.body().getString("mailType");


        JsonArray params = new JsonArray().add(username).add(mailType);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_MAIL),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);
                message.reply(new JsonObject().put("mail", arr));

            } else {

                reportQueryError(message, res.cause());
            }
        });
    }




    private void postDisposisi(Message<JsonObject> message) {

        JsonObject data = message.body().getJsonObject("data");

        LOGGER.info("postDisposisi get data : "+ data);


        JsonArray listUserNip = data.getJsonArray("disposisi");

        Integer matoId= data.getInteger("matoId");
        Integer mailId= data.getInteger("mailId");
        String msg = data.getString("message");
        Integer userId = data.getInteger("userId");

        LOGGER.info("matoId : "+matoId);
        LOGGER.info("mailId : "+mailId);
        LOGGER.info("userId : "+userId);
        LOGGER.info("message : "+msg);
        LocalDate localTime = LocalDate.now();
       listUserNip.forEach(result ->{
           JsonObject val = new JsonObject(result.toString());
           LOGGER.info("nip : "+val.getString("key"));
           LOGGER.info("nama : "+val.getString("label"));
           String nip = val.getString("key");
           JsonArray params = new JsonArray()
                   .add(mailId)
                   .add(matoId)
                   .add(val.getString("key"))
                   .add("DISPOSISI")
                   .add("NEW")
                   .add(localTime.toString())
                   .add(userId)
                   .add(msg);



           dbClient.updateWithParams(sqlQueries.get(SqlQuery.POST_DISPOSISI),params,res ->{
               if (res.succeeded()){
                 //  message.reply(new JsonObject().put("succeed","true"));

                   JsonArray paramNotif = new JsonArray().add(mailId);
                   dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_STAFF_NOTIF),paramNotif, resPush -> {
                       if (resPush.succeeded()) {
                           JsonArray arr = new JsonArray();
                        //   resPush.result().getRows().forEach(arr::add);

                           for (JsonArray resStaff: resPush.result().getResults()) {
                               arr.add(resStaff.getString(0));
                           }

                            LOGGER.info("staff: "+arr);
                             pushNotif(arr,"DISPOSISI");
                           message.reply(new JsonObject().put("succeed","true"));
                         //  message.reply(new JsonObject().put("mail", arr));


                       } else {
                           reportQueryError(message, resPush.cause());
                       }
                   });


               }  else {
                   reportQueryError(message, res.cause());
               }
           });
       });



    }

    private void getUserLogin(Message<JsonObject> message) {
        JsonObject credential = message.body().getJsonObject("session");

        LOGGER.info("/getuserlogin :" + credential);

        String username = message.body().getJsonObject("session").getString("username");
        LOGGER.info("/username : "+username);
        String user = credential.getString("username");
        String pwd  = credential.getString("password");
        String playerId = credential.getString("playerId");
        LOGGER.info("user/pwd db: "+user+"/"+pwd+"/"+playerId);

        JsonArray params = new JsonArray().add(user);
        JsonArray paramsUpdate = new JsonArray().add(playerId).add(user);

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_LOGIN),params, res -> {
            if (res.succeeded()) {
                LOGGER.info("rows : "+res.result().getNumRows());

                if (res.result().getNumRows() >= 1){
                    String candidate = res.result().getRows().get(0).getString("pega_password").replaceFirst("2y", "2a");

                    if (BCrypt.checkpw(pwd,candidate)) {

                        LOGGER.info("password matched");

                        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_USER),params, resProfile -> {
                            if (resProfile.succeeded()){
                                JsonArray arr = new JsonArray();
                                resProfile.result().getRows().forEach(arr::add);

                                message.reply(new JsonObject().put("epim",arr).put("success","true"));

                                dbClient.updateWithParams(sqlQueries.get(SqlQuery.UPDATE_PLAYER),paramsUpdate,resUpdate ->{
                                    if (resUpdate.succeeded()){
                                        message.reply(new JsonObject().put("succeed","true"));


                                    }  else {
                                        reportQueryError(message, res.cause());
                                    }
                                });

                            }else{
                                reportQueryError(message, resProfile.cause());
                            }

                        });
                    }else{
                        //reportQueryError(message, res.cause());
                        message.reply(new JsonObject().put("success","false"));
                    }
                } else{
                    message.reply(new JsonObject().put("success","false"));
                }


            } else {
                //message.reply(new JsonObject().put("db-reply","user or password doesn't matched."));
                reportQueryError(message, res.cause());
            }
        });
    }


    private void getUser(Message<JsonObject> message) {

        String user = message.body().getString("username");
        String pwd  = message.body().getString("password");

        JsonArray params = new JsonArray().add(user);


        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_USER),params, res -> {
            if (res.succeeded()) {
                JsonArray arr = new JsonArray();
                res.result().getRows().forEach(arr::add);


                List<JsonObject> rows = res.result().getRows();

                String pwdHash=null;

                for (JsonObject row : rows) {

                    pwdHash = row.getString("0");
                    System.out.println("row : "+row.getString("0"));
                }



                message.reply(new JsonObject().put("data", arr));

               /* String candidate = res.result().getRows().get(0).getString("pega_password").replaceFirst("2y", "2a");

                if (BCrypt.checkpw(pwd,candidate)) {

                    JsonArray arr = new JsonArray();
                    res.result().getRows().forEach(arr::add);

                    message.reply(new JsonObject().put("profile", arr));
                }else{
                    message.reply(new JsonObject().put("user-reply","user or password doesn't matched."));
                }*/

            } else {
                reportQueryError(message, res.cause());
            }
        });

    }
    // end::onMessage[]


    private void pushNotif(JsonArray staffs,String mailType){
        LOGGER.info("getPushNotification");
        LOGGER.info("push notif staff: "+staffs);
        String message ="You got mail.";
        if (mailType.equalsIgnoreCase("INBOX")){
            message="Anda mendapatkan surat masuk";
        }else if (mailType.equalsIgnoreCase("OUTBOX")){
            message="Anda mendapatkan surat keluar";
        }else if (mailType.equalsIgnoreCase("DISPOSISI")){
            message="Anda mendapatkan surat disposisi";
        }else if (mailType.equalsIgnoreCase("DRAFT")){
            message = "Anda mendapatkan surat draft";
        }




        PushClient.create(vertx,oneSignalApiId, onesignalApiKey).
                withContent(new JsonObject().put("en", "Disposisi Delivered.").put("en",message)).
                //add a heading
                        withHeadings(new JsonObject().put("en","EOffice").put("en","Cimahi SmartCity")).
                //all users should receive this
                targetByPlayerIds(staffs).
                       // targetBySegments(Segments.ALL).
                sendNow(
                        h -> {
                            if (h.succeeded()) {
                                System.err.println(h.result().encodePrettily());
                                LOGGER.info("send notif succeedd");
                            } else {
                                h.cause().printStackTrace();
                                LOGGER.info("send notif failed.");
                            }

                        });
    }




    // tag::onMessage[]
    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private void reportQueryError(Message<JsonObject> message, Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }
}
