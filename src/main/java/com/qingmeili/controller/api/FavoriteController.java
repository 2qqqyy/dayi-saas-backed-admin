package com.qingmeili.controller.api;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.favorite.FavoriteDTO;
import com.qingmeili.entity.Favorite;
import com.qingmeili.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端 - 收藏
 */
@RestController
@RequestMapping("/api/user/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public Result<List<Favorite>> list(@RequestParam(required = false) String type) {
        return favoriteService.list(UserContext.getUserId(), type);
    }

    @PostMapping
    public Result<Favorite> add(@Valid @RequestBody FavoriteDTO dto) {
        return favoriteService.add(UserContext.getUserId(), dto);
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        return favoriteService.remove(id, UserContext.getUserId());
    }
}
