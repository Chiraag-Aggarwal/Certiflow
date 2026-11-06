package com.certiflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PrivateRemarksTest {
    @TempDir Path directory;
    TestBackend backend;AppData data;
    @BeforeEach void setup() throws Exception {backend=new TestBackend();data=new AppData(directory,backend.client());}
    @AfterEach void close(){backend.close();}
    void login(AppData.Role role)throws Exception{backend.role=role;data.authenticate("account0@example.invalid","long-password-test");}
    @Test void sendsOnlyItemAndRemarkWhileIdentityComesFromServer() throws Exception {
        login(AppData.Role.TEACHER);
        var saved=data.addPrivateRemark(AppData.RemarkSource.PROJECT,TestBackend.TEACHER_ID,"  Explain your experiment results.  ");
        assertEquals("Explain your experiment results.",saved.body());
        assertEquals(Set.of("source_kind","source_id","remark_text"),backend.lastBody.keySet());
        assertEquals("Registered staff member",saved.authorName());
        assertEquals(1,data.privateRemarks(AppData.RemarkSource.PROJECT,TestBackend.TEACHER_ID,0).size());
        assertTrue(data.privateRemarks(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,0).isEmpty());
    }
    @Test void studentsCanReadButCannotWriteAndViewersCannotRead() throws Exception {
        login(AppData.Role.ADMIN);data.addPrivateRemark(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,"Private feedback");
        login(AppData.Role.STUDENT);assertEquals(1,data.privateRemarks(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,0).size());
        backend.calls.clear();assertThrows(IllegalArgumentException.class,()->data.addPrivateRemark(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,"Forged"));
        assertTrue(backend.calls.isEmpty());
        login(AppData.Role.VIEWER);backend.calls.clear();assertThrows(IllegalArgumentException.class,()->data.privateRemarks(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,0));assertTrue(backend.calls.isEmpty());
    }
    @Test void validationAndServerErrorsNeverBecomeASuccess() throws Exception {
        login(AppData.Role.ADMIN);backend.calls.clear();
        assertThrows(IllegalArgumentException.class,()->data.addPrivateRemark(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID," "));
        assertThrows(IllegalArgumentException.class,()->data.addPrivateRemark(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,"x".repeat(2001)));
        assertThrows(IllegalArgumentException.class,()->data.privateRemarks(AppData.RemarkSource.PROJECT,"invalid",0));assertTrue(backend.calls.isEmpty());
        backend.remarksUnavailable=true;assertThrows(java.io.IOException.class,()->data.addPrivateRemark(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,"Useful feedback"));
        assertThrows(java.io.IOException.class,()->data.privateRemarks(AppData.RemarkSource.NOC,TestBackend.TEACHER_ID,0));assertTrue(backend.remarks.isEmpty());
    }
}
