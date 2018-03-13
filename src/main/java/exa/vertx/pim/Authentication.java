package exa.vertx.pim;

import exa.vertx.util.Runner;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.JsonParser;
import org.mindrot.jbcrypt.BCrypt;

public class Authentication extends AbstractVerticle {

    public static void main(String[] args) {
        Runner.runExample(Authentication.class);
    }

    @Override
    public void start() throws Exception {

        String password= "$2y$10$2ypXIh49WhZcjJGmOST5QOXi7jetwe8AjLCPXYx.7hCfVas/E6mye".replaceFirst("2y", "2a");
        String  candidate = "rahasia";


        String pwdHas=BCrypt.hashpw(candidate,BCrypt.gensalt(8));

        System.out.println("candidate hash : "+pwdHas);


        if (BCrypt.checkpw(candidate, password)) {
            System.out.println("It matches");
        }

        else {
            System.out.println("It does not match");
        }

        JsonParser parser = JsonParser.newParser();

// start array event
// start object event
// "firstName":"Bob" event
        parser.handle(Buffer.buffer("[{\"firstName\":\"Bob\","));

// "lastName":"Morane" event
// end object event
        parser.handle(Buffer.buffer("\"lastName\":\"Morane\"},"));

// start object event
// "firstName":"Luke" event
// "lastName":"Lucky" event
// end object event
        parser.handle(Buffer.buffer("{\"firstName\":\"Luke\",\"lastName\":\"Lucky\"}"));

// end array event
        parser.handle(Buffer.buffer("]"));

        System.out.println(parser.resume());
// Always call end
        parser.end();


        /*String  originalPassword = "$2y$10$AtjIxCQWXpFKXp2PbA.FEOtFPT46jc1OU8J1V1WqBC3SC1L5rTSM2";
        String generatedSecuredPasswordHash = BCrypt.hashpw(originalPassword, BCrypt.gensalt(12));
        System.out.println(generatedSecuredPasswordHash);

        boolean matched = BCrypt.checkpw(originalPassword, generatedSecuredPasswordHash);
        System.out.println(matched);*/
    }
}
