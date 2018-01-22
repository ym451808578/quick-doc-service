package cn.mxleader.quickdoc.web.controller;

import cn.mxleader.quickdoc.security.session.ActiveUser;
import cn.mxleader.quickdoc.service.ReactiveDirectoryService;
import cn.mxleader.quickdoc.service.ReactiveUserService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.stream.Collectors;

import static cn.mxleader.quickdoc.common.CommonCode.HOME_TITLE;
import static cn.mxleader.quickdoc.common.CommonCode.SESSION_USER;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ReactiveUserService reactiveUserService;
    private final ReactiveDirectoryService reactiveDirectoryService;

    @Autowired
    public AdminController(ReactiveUserService reactiveUserService,
                           ReactiveDirectoryService reactiveDirectoryService) {
        this.reactiveUserService = reactiveUserService;
        this.reactiveDirectoryService = reactiveDirectoryService;
    }

    /**
     * 获取系统标题
     *
     * @return
     */
    @ModelAttribute("title")
    public String pageTitle() {
        return HOME_TITLE;
    }

    /**
     * 登录后的首页
     *
     * @param model
     * @return
     */
    @GetMapping()
    public String index(Model model) {
        model.addAttribute("adminPage", "user-admin");
        model.addAttribute("users", reactiveUserService.findAllUsers()
                .toStream()
                .collect(Collectors.toList()));
        return "admin";
    }

    /**
     * 登录后的首页
     *
     * @param model
     * @return
     */
    @GetMapping("/folder-admin")
    public String folderAdmin(Model model) {
        model.addAttribute("adminPage", "folder-admin");
        model.addAttribute("directories", reactiveDirectoryService.findAllInWebFormat()
                .toStream()
                .collect(Collectors.toList()));
        return "admin";
    }

    /**
     * 删除文件夹
     *
     * @param directoryId
     * @param session
     * @param redirectAttributes
     * @return
     */
    @DeleteMapping("/deleteDirectory")
    public String deleteDirectory(@RequestParam("directoryId") ObjectId directoryId,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        if (activeUser.isAdmin()) {
            reactiveDirectoryService.deleteDirectory(directoryId).subscribe();
            redirectAttributes.addFlashAttribute("message",
                    "成功删除文件夹： " + directoryId);
        } else {
            redirectAttributes.addFlashAttribute("message",
                    "您无删除文件夹的权限，请联系管理员获取！");
        }
        return "redirect:/admin/folder-admin";
    }

    /**
     * 登录后的首页
     *
     * @param model
     * @return
     */
    @GetMapping("/swagger-ui")
    public String swaggerUI(Model model) {
        model.addAttribute("adminPage", "swagger-ui");
        return "admin";
    }

    /**
     * 删除系统用户信息
     *
     * @param userId
     * @param session
     * @param redirectAttributes
     * @return
     */
    @DeleteMapping("/deleteUser")
    public String deleteUser(@RequestParam("userId") ObjectId userId,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        if (activeUser.isAdmin()) {
            reactiveUserService.deleteUserById(userId).subscribe();
            redirectAttributes.addFlashAttribute("message",
                    "成功删除用户： " + userId);
        } else {
            redirectAttributes.addFlashAttribute("message",
                    "您无删除用户的权限，请联系管理员获取！");
        }
        return "redirect:/admin";
    }

}
