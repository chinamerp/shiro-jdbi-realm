package io.ifar.security.web;

import io.ifar.security.realm.JdbiShiroRealm;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.web.env.WebEnvironment;
import org.apache.shiro.web.util.WebUtils;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This needs to be done a) after the Shiro EnvironmentLoader has run, and b) when we have the ServletContext.
 * Hence we can't do this directly in the service's run() method as the servlet is still being configured at that point.
 * This ServletContextListener meets our needs and lets the configuration occur.
 * <p>
 * This implementation assumes that there is a Shiro {@link RealmSecurityManager} instance in use.
 * </p>
 * <p>
 * If you use this class you must provide the shiro-web and the javax.servlet packages.
 * </p>
 * <p/>
 * Project: jdbi-realm
 * DefaultUserImpl: ezra
 * Date: 4/6/13
 */
public class JdbiRealmLoaderListener implements ServletContextListener {

    /**
     * Used to control which Realm instance/s is/are initialized.
     */
    public static enum RealmSelector {
        /**
         * Initialize all instances of JdbiShiroRealm configured in the current SecurityManager.
         */
        ALL,

        /**
         * Only initialize the first JdbiShiroRealm encountered.
         */
        FIRST
        /* , NAMED */ // Named might be a good option.  OR could be based on 'name of variable' in shiro.ini config...
    }

    private static final Logger LOG = LoggerFactory.getLogger(JdbiRealmLoaderListener.class);

    private final DBI jdbi;
    private final RealmSelector whichRealm;

    /**
     * Constructs an instance with the provided {@link DBI} instance, and {@link RealmSelector#ALL}.
     *
     * @param jdbi a DBI instance
     */
    public JdbiRealmLoaderListener(DBI jdbi) {
        this(jdbi, RealmSelector.ALL);
    }

    public DBI getDbi() {
        return jdbi;
    }

    /**
     * Constructs an instance with the provided {@link DBI} instance, and provided {@link RealmSelector}.
     *
     * @param jdbi       a DBI instance, cannot be null
     * @param whichRealm a RealmSelector value, defaults to {@link RealmSelector#ALL}
     */
    public JdbiRealmLoaderListener(DBI jdbi, RealmSelector whichRealm) {
        checkArgument(jdbi != null, "jdbi is a required argument");
        this.jdbi = jdbi;
        if (whichRealm == null) {
            whichRealm = RealmSelector.ALL;
            LOG.info("no RealmSelector specified, defaulting to {}", whichRealm);
        }
        this.whichRealm = whichRealm;
    }

    /**
     * Gets the RealmSecurityManager from the Shiro WebEnvironment. The configured Shiro SecurityManager must be an
     * instance of {@link RealmSecurityManager}.
     *
     * @param sce used to get the ServletContext and from it the WebEnvironment.
     * @return the Shiro {@code SecurityManager} cast to {@link RealmSecurityManager}
     */
    protected RealmSecurityManager getRealmSecurityManager(ServletContextEvent sce) {
        WebEnvironment we = WebUtils.getWebEnvironment(sce.getServletContext());
        return (RealmSecurityManager) we.getSecurityManager();
    }


    /**
     * Subclasses should override this method to change how Realm/s are initialized.
     * Subclass implementations should ordinarily call this super impl.
     * <p>
     *     This implementation calls {@link JdbiShiroRealm#setDbi(org.skife.jdbi.v2.DBI)},
     *     passing in the {@code DBI} instance from our own {@link #getDbi()} method.
     * </p>
     *
     * @param realm the JdbiShiroRealm or subclass thereof being initialized
     */
    protected void initializeRealm(JdbiShiroRealm realm) {
        LOG.debug("initializing JdbiShiroRealm '{}' with DBI instance", realm.getName());
        realm.setDbi(getDbi());
    }

    /**
     * @param sce used to get the SecurityManager that has the Realms
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        RealmSecurityManager rsm = getRealmSecurityManager(sce);
        for (Realm r : rsm.getRealms()) {
            if (r instanceof JdbiShiroRealm) {
                initializeRealm((JdbiShiroRealm) r);
                if (whichRealm == RealmSelector.FIRST) {
                    break;
                }
            }
        }

    }

    /**
     * Subclasses should override this method to change how Realm/s are tidied up after use.
     * Subclass implementations should ordinarily call this super impl.
     * <p>
     *     This implementation calls {@link JdbiShiroRealm#close(org.skife.jdbi.v2.DBI)},
     *     passing in the {@code DBI} instance from our own {@link #getDbi()} method.

     * </p>
     *
     * @param realm the JdbiShiroRealm or subclass thereof that will no longer be used.
     */
    protected void destroyRealm(JdbiShiroRealm realm) {
        LOG.debug("closing JdbiShiroRealm's DBI-based DAO instance/s.", realm.getName());
        realm.close(getDbi());
    }

    /**
     * When the app shuts down we close the UserDAOs on the JdbiShiroRealm instances we initialized.
     *
     * @param sce used to get the SecurityManager that has the Realms
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        RealmSecurityManager rsm = getRealmSecurityManager(sce);
        for (Realm r : rsm.getRealms())
            if (r instanceof JdbiShiroRealm) {
                destroyRealm((JdbiShiroRealm) r);
                if (whichRealm == RealmSelector.FIRST) {
                    break;
                }
            }
    }
}
