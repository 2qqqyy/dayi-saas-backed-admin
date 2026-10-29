package com.qingmeili.controller.api;

import com.qingmeili.common.context.UserContext;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.user.AddressDTO;
import com.qingmeili.entity.Address;
import com.qingmeili.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端 - 收货地址
 */
@RestController
@RequestMapping("/api/user/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public Result<List<Address>> list() {
        return addressService.list(UserContext.getUserId());
    }

    @PostMapping
    public Result<Address> create(@Valid @RequestBody AddressDTO dto) {
        return addressService.create(UserContext.getUserId(), dto);
    }

    @PutMapping("/{id}")
    public Result<Address> update(@PathVariable Long id, @Valid @RequestBody AddressDTO dto) {
        return addressService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return addressService.delete(id);
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        return addressService.setDefault(id, UserContext.getUserId());
    }
}
