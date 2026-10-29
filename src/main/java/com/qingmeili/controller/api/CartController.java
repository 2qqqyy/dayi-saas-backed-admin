package com.qingmeili.controller.api;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.cart.CartAddDTO;
import com.qingmeili.entity.CartItem;
import com.qingmeili.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C端 - 购物车
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return cartService.list(UserContext.getUserId());
    }

    @PostMapping
    public Result<CartItem> add(@Valid @RequestBody CartAddDTO dto) {
        return cartService.add(UserContext.getUserId(), dto);
    }

    @PutMapping("/{id}/quantity")
    public Result<Void> updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        return cartService.updateQuantity(UserContext.getUserId(), id, quantity);
    }

    @PutMapping("/{id}/select")
    public Result<Void> updateSelect(@PathVariable Long id, @RequestParam Integer selected) {
        return cartService.updateSelect(UserContext.getUserId(), id, selected);
    }

    @PutMapping("/select-all")
    public Result<Void> selectAll(@RequestParam Integer selected) {
        return cartService.selectAll(UserContext.getUserId(), selected);
    }

    @PostMapping("/remove")
    public Result<Void> remove(@RequestBody Map<String, List<Long>> body) {
        return cartService.remove(UserContext.getUserId(), body.get("ids"));
    }

    @DeleteMapping("/items/selected")
    public Result<Void> clearSelected() {
        return cartService.clearSelected(UserContext.getUserId());
    }

    @DeleteMapping("/items")
    public Result<Void> clearAll() {
        return cartService.clearAll(UserContext.getUserId());
    }
}
