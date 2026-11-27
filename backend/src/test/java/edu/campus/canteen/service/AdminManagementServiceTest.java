package edu.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.campus.canteen.dto.CategoryStatusRequest;
import edu.campus.canteen.dto.CategoryCreateRequest;
import edu.campus.canteen.dto.UserStatusRequest;
import edu.campus.canteen.entity.Category;
import edu.campus.canteen.entity.User;
import edu.campus.canteen.exception.BusinessException;
import edu.campus.canteen.exception.ErrorCode;
import edu.campus.canteen.mapper.CategoryMapper;
import edu.campus.canteen.mapper.UserMapper;
import edu.campus.canteen.security.JwtService;
import edu.campus.canteen.service.impl.CategoryServiceImpl;
import edu.campus.canteen.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminManagementServiceTest {
    @Test
    void categoryCreateDefaultsToEnabled() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        when(mapper.insert(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(5L);
            return 1;
        });
        when(mapper.selectById(5L)).thenAnswer(invocation -> {
            Category category = new Category();
            category.setId(5L);
            category.setName("饮品");
            category.setSortOrder(2);
            category.setStatus(1);
            return category;
        });

        var result = new CategoryServiceImpl(mapper).createCategory(new CategoryCreateRequest("饮品", 2));

        assertEquals(1, result.status());
        verify(mapper).insert(any(Category.class));
    }

    @Test
    void categoryStatusCanBeChangedAndReturned() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        Category category = new Category();
        category.setId(4L);
        category.setName("饮品");
        category.setSortOrder(2);
        category.setStatus(1);
        when(mapper.selectById(4L)).thenReturn(category);
        when(mapper.updateById(category)).thenReturn(1);

        var result = new CategoryServiceImpl(mapper).updateCategoryStatus(4L, new CategoryStatusRequest(0));

        assertEquals(0, result.status());
        verify(mapper).updateById(category);
    }

    @Test
    void updatingMissingCategoryReturnsNotFound() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new CategoryServiceImpl(mapper).updateCategoryStatus(99L, new CategoryStatusRequest(1)));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(mapper, never()).updateById(any(Category.class));
    }

    @Test
    void adminCannotDisableOwnAccount() {
        UserMapper mapper = mock(UserMapper.class);
        User admin = new User();
        admin.setId(3L);
        admin.setRole("ADMIN");
        admin.setStatus(1);
        when(mapper.selectById(3L)).thenReturn(admin);
        UserServiceImpl service = new UserServiceImpl(mapper, mock(PasswordEncoder.class), mock(JwtService.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAdminUserStatus(3L, 3L, new UserStatusRequest(0)));

        assertEquals(ErrorCode.ADMIN_CANNOT_DISABLE_SELF, exception.getErrorCode());
        verify(mapper, never()).updateById(any(User.class));
    }

    @Test
    void adminUserListViewDoesNotExposePasswordHash() {
        assertFalse(java.util.Arrays.stream(edu.campus.canteen.vo.AdminUserVO.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("password")));
    }

    @Test
    void adminCanDisableAnotherUserWithoutChangingTheirRole() {
        UserMapper mapper = mock(UserMapper.class);
        User student = new User();
        student.setId(8L);
        student.setRole("STUDENT");
        student.setStatus(1);
        when(mapper.selectById(8L)).thenReturn(student);
        when(mapper.updateById(student)).thenReturn(1);
        UserServiceImpl service = new UserServiceImpl(mapper, mock(PasswordEncoder.class), mock(JwtService.class));

        var result = service.updateAdminUserStatus(3L, 8L, new UserStatusRequest(0));

        assertEquals(0, result.status());
        assertEquals("STUDENT", result.role());
        verify(mapper).updateById(student);
    }
}
