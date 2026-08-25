
package moe.yushi.authlibinjector.httpd;

import java.util.Optional;

public interface URLRedirector {
    public Optional<String> redirect(String var1, String var2);
}

