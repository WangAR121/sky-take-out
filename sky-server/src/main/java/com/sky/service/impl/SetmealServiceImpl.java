package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.SetmealDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal=new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.insert(setmeal);
        Long setDishId=setmeal.getId();
        List<SetmealDish> setmealDishes=setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setDishId);
        });
        setmealDishMapper.insertBatch(setmealDishes);
    }

    @Override
    public PageResult page(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<SetmealVO> page=setmealMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteBatch(List<Long> ids) {
        List<Integer> statusLIst=setmealMapper.selectByIds(ids);
        statusLIst.forEach(status->{
            if(StatusConstant.ENABLE.equals(status))
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        });
        setmealMapper.deleteByIds(ids);
        setmealDishMapper.deleteByIds(ids);
    }

    @Override
    public SetmealVO getByIdWithDishes(Long id) {
        List<SetmealDish> setmealDishes=setmealDishMapper.getBySetmealId(id);
        Setmeal setmeal=setmealMapper.getById(id);
        SetmealVO setmealVO=new SetmealVO();
        BeanUtils.copyProperties(setmeal,setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal=new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.update(setmeal);
        Long setmealId=setmeal.getId();
        List<Long> setmealIdList= Arrays.asList(setmealId);
        setmealDishMapper.deleteByIds(setmealIdList);
        List<SetmealDish> setmealDishes=setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });
        setmealDishMapper.insertBatch(setmealDishes);
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        if (StatusConstant.ENABLE.equals(status)){
            List<Dish> dishList=dishMapper.getBySetmealId(id);
            dishList.forEach(dish -> {
                if(StatusConstant.DISABLE.equals(dish.getStatus()))
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
            });
        }
        Setmeal setmeal=Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }
}
