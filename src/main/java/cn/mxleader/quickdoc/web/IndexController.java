package cn.mxleader.quickdoc.web;

import cn.mxleader.quickdoc.common.utils.StringUtil;
import cn.mxleader.quickdoc.entities.FsDescription;
import cn.mxleader.quickdoc.entities.FsDirectory;
import cn.mxleader.quickdoc.entities.FsOwner;
import cn.mxleader.quickdoc.security.entities.ActiveUser;
import cn.mxleader.quickdoc.service.*;
import cn.mxleader.quickdoc.web.domain.WebDirectory;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.mxleader.quickdoc.common.CommonCode.SESSION_USER;
import static cn.mxleader.quickdoc.common.AuthenticationHandler.*;

@Controller
@RequestMapping("/")
@SessionAttributes("ActiveUser")
public class IndexController {

    private final ReactiveCategoryService reactiveCategoryService;
    private final ReactiveDirectoryService reactiveDirectoryFsService;
    private final ReactiveFileService reactiveFileService;
    private final QuickDocConfigService quickDocConfigService;
    private final StreamService streamService;

    @Autowired
    public IndexController(ReactiveCategoryService reactiveCategoryService,
                           ReactiveDirectoryService reactiveDirectoryFsService,
                           ReactiveFileService reactiveFileService,
                           QuickDocConfigService quickDocConfigService,
                           StreamService streamService) {
        this.reactiveCategoryService = reactiveCategoryService;
        this.reactiveDirectoryFsService = reactiveDirectoryFsService;
        this.reactiveFileService = reactiveFileService;
        this.quickDocConfigService = quickDocConfigService;
        this.streamService = streamService;
    }

    @ModelAttribute("categoryMap")
    public Map<ObjectId, String> getCategoryMap() {
        return reactiveCategoryService.findAll()
                .collectMap(
                        fsCategory -> fsCategory.getId(),
                        fsCategory -> fsCategory.getType())
                .block();
    }

    /**
     * 登录后的首页
     *
     * @param model
     * @return
     */
    @GetMapping()
    public String index(Model model, HttpSession session) {
        ObjectId rootParentId = quickDocConfigService.getQuickDocConfig().getId();
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        model.addAttribute("isAdmin", activeUser.isAdmin());
        if (activeUser.isAdmin()) {
            refreshDirList(model, rootParentId, session);
        } else {
            List<WebDirectory> directories = reactiveDirectoryFsService.findAllByParentIdInWebFormat(rootParentId)
                    .filter(webDirectory -> webDirectory.getPath().equalsIgnoreCase("root"))
                    .toStream()
                    .collect(Collectors.toList());
            if (directories != null && directories.size() > 0) {
                for (WebDirectory subdirectory : directories) {
                    model.addAttribute("currentdirectory", subdirectory);
                    refreshDirList(model, subdirectory.getId(), session);
                }
            }
        }
        return "index";
    }

    /**
     * 刷新显示指定文件夹内的所有内容
     *
     * @param directoryId
     * @param model
     * @return
     */
    @GetMapping("/path@{directoryId}")
    public String index(@PathVariable ObjectId directoryId, Model model, HttpSession session) {
        FsDirectory fsDirectory = reactiveDirectoryFsService.findById(directoryId).block();
        model.addAttribute("currentdirectory", fsDirectory);
        refreshDirList(model, directoryId, session);
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        model.addAttribute("isAdmin", activeUser.isAdmin());

        return "index";
    }

    /**
     * 打包下载指定文件夹内的所有内容
     *
     * @param response
     * @param directoryId
     * @throws IOException
     */
    @GetMapping(value = "/zip-resource/{directoryId}", produces = MediaType.MULTIPART_FORM_DATA_VALUE)
    public @ResponseBody
    void downloadDocument(HttpServletResponse response,
                          @PathVariable ObjectId directoryId,
                          HttpSession session) throws IOException {
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition",
                "attachment; filename=" + directoryId + ".zip");
        //response.setHeader("Content-Length", String.valueOf(fsResource.contentLength()));

        reactiveFileService.createZip(directoryId, response.getOutputStream(), null, activeUser);
    }

    /**
     * 文件下载： 提供文件ID , 下载文件名默认编码为GB2312.
     *
     * @param response
     * @param storedId 文件存储ID号
     * @throws IOException
     */
    @GetMapping(value = "/download/{storedId}", produces = MediaType.MULTIPART_FORM_DATA_VALUE)
    public @ResponseBody
    void downloadDocument(HttpServletResponse response,
                          @PathVariable String storedId) throws IOException {
        GridFSDownloadStream fs = reactiveFileService.getFileStream(new ObjectId(storedId));
        GridFSFile gridFSFile = fs.getGridFSFile();

        response.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        response.setHeader("Content-Disposition",
                "attachment; filename=" + new String(gridFSFile.getFilename()
                        .getBytes("gb2312"), "ISO8859-1"));
        response.setHeader("Content-Length", String.valueOf(gridFSFile.getLength()));
        FileCopyUtils.copy(fs, response.getOutputStream());
    }

    /**
     * PDF格式文件预览功能
     *
     * @param storedId
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/view-pdf/{storedId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public @ResponseBody
    HttpEntity<byte[]> openPdfEntity(@PathVariable String storedId) throws IOException {
        return openDocumentEntity(MediaType.APPLICATION_PDF, storedId);
    }

    /**
     * GIF预览功能
     *
     * @param storedId
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/view-gif/{storedId}", produces = MediaType.IMAGE_GIF_VALUE)
    public @ResponseBody
    HttpEntity<byte[]> openImageGifEntity(@PathVariable String storedId) throws IOException {
        return openDocumentEntity(MediaType.IMAGE_GIF, storedId);
    }

    /**
     * JPEG预览功能
     *
     * @param storedId
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/view-jpeg/{storedId}", produces = MediaType.IMAGE_JPEG_VALUE)
    public @ResponseBody
    HttpEntity<byte[]> openImageJpegEntity(@PathVariable String storedId) throws IOException {
        return openDocumentEntity(MediaType.IMAGE_JPEG, storedId);
    }

    /**
     * PNG预览功能
     *
     * @param storedId
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/view-png/{storedId}", produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody
    HttpEntity<byte[]> openImageEntity(@PathVariable String storedId) throws IOException {
        return openDocumentEntity(MediaType.IMAGE_PNG, storedId);
    }

    private HttpEntity<byte[]> openDocumentEntity(MediaType returnType, String storedId) throws IOException {
        GridFSDownloadStream fs = reactiveFileService.getFileStream(new ObjectId(storedId));
        byte[] document = FileCopyUtils.copyToByteArray(fs);

        HttpHeaders header = new HttpHeaders();
        header.setContentType(returnType);
        header.set("Content-Disposition", "inline; filename=" + fs.getGridFSFile().getFilename());
        header.setContentLength(document.length);

        return new HttpEntity<>(document, header);
    }

    /**
     * 上传文件到指定目录和文件分类
     *
     * @param file
     * @param directoryId
     * @param categoryId
     * @param redirectAttributes
     * @param model
     * @param session
     * @return
     * @throws IOException
     */
    @PostMapping("/uploadFile")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam("directoryId") ObjectId directoryId,
                             @RequestParam("categoryId") ObjectId categoryId,
                             @RequestParam(value = "owners", required = false) String[] ownersRequest,
                             RedirectAttributes redirectAttributes,
                             Model model,
                             HttpSession session) throws IOException {
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        FsDirectory fsDirectory = reactiveDirectoryFsService.findById(directoryId).block();
        // 鉴权检查
        if (checkAuthentication(fsDirectory.getPublicVisible(), fsDirectory.getOwners(), activeUser, WRITE_PRIVILEGE)) {
            FsOwner owner = new FsOwner(activeUser.getUsername(), FsOwner.Type.TYPE_PRIVATE, 7);
            List<FsOwner> fsOwnerList = new ArrayList<FsOwner>();
            FsOwner[] fsOwnerDesc = new FsOwner[]{};
            fsOwnerList.add(owner);
            Boolean publicVisible = false;
            if (ownersRequest != null && ownersRequest.length > 0) {
                for (String item : ownersRequest) {
                    if (item.equalsIgnoreCase("PublicMode")) {
                        //fsOwnerList.add(new FsOwner("public", FsOwner.Type.TYPE_PUBLIC, 1));
                        publicVisible = true;
                    } else if (item.equalsIgnoreCase("GroupMode")) {
                        for (String group : activeUser.getGroups()) {
                            fsOwnerList.add(new FsOwner(group, FsOwner.Type.TYPE_GROUP, 3));
                        }
                    }
                }
            }
            String filename = StringUtil.getFilename(file.getOriginalFilename());
            FsDescription fsDescription = new FsDescription(ObjectId.get(),
                    filename,
                    file.getSize(),
                    StringUtils.getFilenameExtension(filename).toLowerCase(),
                    new Date(),
                    categoryId,
                    directoryId,
                    ObjectId.get(),
                    publicVisible,
                    fsOwnerList.toArray(fsOwnerDesc));

            reactiveFileService.storeFile(
                    fsDescription,
                    file.getInputStream())
                    .subscribe();
            refreshDirList(model, directoryId, session);
            // 发送MQ消息
            streamService.sendMessage("用户" + activeUser.getUsername() +
                    "成功上传文件： " + filename + "到目录：" + directoryId);
            redirectAttributes.addFlashAttribute("message",
                    "成功上传文件： " + filename);
        } else {
            redirectAttributes.addFlashAttribute("message",
                    "您无此目录的上传权限： " + fsDirectory.getPath() + "，请联系管理员获取！");
        }
        return "redirect:/path@" + directoryId;
    }

    /**
     * 删除文件
     *
     * @param directoryId
     * @param fsDetailId
     * @return
     */
    @DeleteMapping("/deleteFile")
    public String deleteFile(@RequestParam("directoryId") ObjectId directoryId,
                             @RequestParam ObjectId fsDetailId,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        FsDescription fsDescription = reactiveFileService.getStoredFile(fsDetailId).block();
        if (checkAuthentication(fsDescription.getOpenVisible(), fsDescription.getOwners(), activeUser, DELETE_PRIVILEGE)) {
            reactiveFileService.deleteFile(fsDetailId).subscribe();
            redirectAttributes.addFlashAttribute("message",
                    "成功删除文件： " + fsDetailId);
        } else {
            redirectAttributes.addFlashAttribute("message",
                    "您无删除此文件的权限： " + fsDescription.getFilename() + "，请联系管理员获取！");
        }
        return "redirect:/path@" + directoryId;
    }

    /**
     * 刷新文件夹目录内容
     *
     * @param model
     * @param directoryId
     */
    private void refreshDirList(Model model, ObjectId directoryId,
                                HttpSession session) {
        ActiveUser activeUser = (ActiveUser) session.getAttribute(SESSION_USER);
        model.addAttribute("directories",
                reactiveDirectoryFsService.findAllByParentIdInWebFormat(directoryId)
                        .filter(webDirectory -> checkAuthentication(webDirectory.getPublicVisible(), webDirectory.getOwners(),
                                activeUser, READ_PRIVILEGE))
                        .toStream()
                        .collect(Collectors.toList()));
        model.addAttribute("files",
                reactiveFileService.getStoredFiles(directoryId)
                        .filter(fsDetail -> checkAuthentication(fsDetail.getOpenVisible(), fsDetail.getOwners(),
                                activeUser, READ_PRIVILEGE))
                        .toStream()
                        .collect(Collectors.toList()));
    }


    /**
     * 同名文件多并发请求时有冲突可能, 占用服务端本地缓存目录，需要定期清理Web服务器缓存目录
     *
     * @param response
     * @param filename
     * @return
     * @throws IOException
     *//*
    @GetMapping(value = "/resource/{filename:.+}", produces = MediaType.APPLICATION_PDF_VALUE)
    public @ResponseBody
    Resource resourceDownload(HttpServletResponse response,
                              @PathVariable("filename") String filename) throws IOException {
        GridFsResource fsResource = reactiveFileService.getFileStream(new ObjectId(filename));
        InputStream in = fsResource.getInputStream();

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=" + filename);
        response.setHeader("Content-Length", String.valueOf(fsResource.contentLength()));
        return new FileSystemResource(getTempResourceFile(in, LOCAL_TEMP_DIR + filename));
    }
    *//*
    private File getTempResourceFile(InputStream in, String tempFilename) {
        try {
            File f = new File(tempFilename);
            FileOutputStream out = new FileOutputStream(f);
            byte buf[] = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
                out.write(buf, 0, len);
            out.close();
            in.close();
            return f;
        } catch (IOException e) {
            return null;
        }
    }*/

}
