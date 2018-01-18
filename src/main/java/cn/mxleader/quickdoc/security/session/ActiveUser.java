package cn.mxleader.quickdoc.security.session;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.util.Collection;
import java.util.Iterator;

import static cn.mxleader.quickdoc.security.config.WebSecurityConfig.AUTHORITY_ADMIN;

@Document
public class ActiveUser implements HttpSessionBindingListener {

    private static final String APP_USER_STORE = "ActiveUserStore";

    private String username;
    private Boolean admin;
    private String group;

    private Collection<? extends GrantedAuthority> authorities;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Boolean isAdmin() {
        return admin;
    }

    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = authorities;
    }

    public ActiveUser(String username, String group, Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.group = group;
        this.authorities = authorities;
        Iterator<? extends GrantedAuthority> it = authorities.iterator();
        while (it.hasNext()) {
            GrantedAuthority entry = it.next();
            if (entry.getAuthority().equalsIgnoreCase(AUTHORITY_ADMIN)) {
                this.admin = true;
                break;
            } else {
                this.admin = false;
            }
        }
        // 以下代码判断无效
        //this.isAdmin = authorities.contains(new WebAuthority("ADMIN"));
    }

    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        HttpSession session = event.getSession();
        ServletContext application = session.getServletContext();
        // 把用户名放入在线列表
        ActiveUserStore activeUserStore = (ActiveUserStore) application.getAttribute(APP_USER_STORE);
        // 第一次使用前，需要初始化
        if (activeUserStore == null) {
            activeUserStore = new ActiveUserStore();
            application.setAttribute(APP_USER_STORE, activeUserStore);
        }
        activeUserStore.addUser(this.username);
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        HttpSession session = event.getSession();
        ServletContext application = session.getServletContext();

        // 从在线列表中删除用户名
        ActiveUserStore activeUserStore = (ActiveUserStore) application.getAttribute(APP_USER_STORE);
        activeUserStore.removeUser(this.username);
    }
}