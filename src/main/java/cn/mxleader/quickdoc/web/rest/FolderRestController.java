package cn.mxleader.quickdoc.web.rest;

import cn.mxleader.quickdoc.entities.AuthTarget;
import cn.mxleader.quickdoc.entities.ParentLink;
import cn.mxleader.quickdoc.service.FolderService;
import cn.mxleader.quickdoc.web.domain.TreeNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/folder")
@Api(value = "Folder Configuration API", description = "目录配置修改接口")
public class FolderRestController {

    private final FolderService folderService;

    @Autowired
    FolderRestController(FolderService folderService) {
        this.folderService = folderService;
    }

    @RequestMapping(value = "/tree", method = RequestMethod.GET)
    @ApiOperation(value = "根据磁盘ID号获取目录树信息")
    public List<TreeNode> getFolderTree(@RequestParam String parentId) {
        return folderService.getFolderTree(new ParentLink(new ObjectId(parentId), AuthTarget.DISK));
    }

/*

    @GetMapping("/list")
    @ApiOperation(value = "获取根目录列表")
    public Flux<WebFolder> list() {
        return folderService.findAllByParentIdInWebFormat(
                configService.getSysProfile().getId());
    }

    @GetMapping("/list/{parentId}")
    @ApiOperation(value = "根据上级目录ID获取下级目录列表")
    public Flux<WebFolder> list(@PathVariable("parentId") ObjectId parentId) {
        return folderService.findAllByParentIdInWebFormat(parentId);
    }

    @PostMapping(value = "/save",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "保存目录信息")
    public Mono<RestResponse> save(@RequestBody SysFolder folder) {
        return folderService.save(folder)
                .map(SuccessResponse::new);
    }

    @PostMapping(value = "/rename/{folderId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "更改目录名称")
    public Mono<RestResponse> rename(@PathVariable("folderId") ObjectId folderId,
                                     @RequestBody String newPath) {
        Optional<SysFolder> folderOptional = folderService.findById(folderId).blockOptional();
        if (folderOptional.isPresent()) {
            return folderService.rename(folderOptional.get(), newPath)
                    .map(folder -> new SuccessResponse<>("目录改名成功！"));
        } else {
            return Mono.just(new ErrorResponse(0, "目录重命名失败, 请检查新目录名是否有误！"));
        }
    }

    @PostMapping(value = "/move/{folderId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "移动目录位置")
    public Mono<RestResponse> move
            (@PathVariable("folderId") ObjectId folderId,
             @RequestBody String newFolderId) {
        Optional<SysFolder> folderOptional = folderService.findById(folderId).blockOptional();
        if (folderOptional.isPresent()) {
            return folderService.move(folderId, new ObjectId(newFolderId))
                    .map(folder -> new SuccessResponse<>("目录转移成功！"));
        } else {
            return Mono.just(new ErrorResponse(0, "目录转移失败, 请检查新目录ID是否有误！"));
        }
    }

    @DeleteMapping(value = "/delete/{folderId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "删除文件夹")
    public Mono<RestResponse> delete(@PathVariable ObjectId folderId) {
        return folderService.delete(folderId)
                .map(v -> new SuccessResponse<>("删除文件夹成功！"));
    }
*/

}