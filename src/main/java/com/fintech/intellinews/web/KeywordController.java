package com.fintech.intellinews.web;

import com.fintech.intellinews.Result;
import com.fintech.intellinews.base.BaseController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author waynechu
 * Created 2017-10-27 13:29
 */
@RestController
@Api(tags = "关键字资源接口")
@RequestMapping("/v1/keywords")
public class KeywordController extends BaseController {

    @GetMapping("/hot")
    @ResponseBody
    @ApiOperation(value = "获取热门搜索关键字列表", notes = "", produces = "application/json")
    public Result<List<String>> listHotKeywords() {
        return null;
    }

    @GetMapping("/{userId}")
    @ResponseBody
    @ApiOperation(value = "获取用户个人关键字列表", notes = "", produces = "application/json")
    public Result<List<String>> listPersonalKeywords(@PathVariable(value = "userId") Long id) {
        return null;
    }
}