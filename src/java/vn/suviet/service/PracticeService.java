package vn.suviet.service;

import java.nio.file.Files;
import vn.suviet.dao.*;
import vn.suviet.model.Question;

public final class PracticeService {
    public void resubmitImage(int answer,int uid,int revision,UploadService.Image image)throws Exception {
        var previous=new PracticeDao().answer(answer,uid,false);
        if(previous==null||!"ESSAY".equals(previous.get("kind")))throw new AccessDeniedException();
        UploadService uploads=new UploadService();var oldImage=uploads.resolve((String)previous.get("upload_name"));
        if(Files.isRegularFile(oldImage)&&java.security.MessageDigest.isEqual(Files.readAllBytes(oldImage),image.bytes()))
            throw new IllegalArgumentException("Đây là ảnh đã nộp. Em hãy tự làm lại bài và chụp ảnh mới.");
        String file=uploads.save(image);
        try{new EssayDao().resubmit(answer,uid,revision,file,image.mime());}
        catch(Exception e){uploads.remove(file);throw e;}
    }
    public int submitImage(int attempt,int uid,int position,UploadService.Image image)throws Exception {
        UploadService uploads=new UploadService();String file=uploads.save(image);
        try{return new PracticeDao().save(attempt,uid,position,"",null,"Bài làm đã được lưu an toàn. Chọn Nhận gợi ý AI để chấm ảnh.","",file,image.mime());}
        catch(Exception e){uploads.remove(file);throw e;}
    }
    public void grade(int answerId,int uid)throws Exception {
        PracticeDao dao=new PracticeDao();var answer=dao.answer(answerId,uid,false);
        if(answer==null||!"ESSAY".equals(answer.get("kind")))throw new AccessDeniedException();
        if(!dao.claimGrade(answerId,uid))throw new IllegalArgumentException("Bài đã có điểm hoặc đang được xử lý. Nếu yêu cầu trước bị gián đoạn, hãy thử lại sau 3 phút.");
        try{
            byte[] bytes=Files.readAllBytes(new UploadService().resolve((String)answer.get("upload_name")));
            var result=new TutorService().grade(Question.restore((String)answer.get("question_snapshot")),bytes,(String)answer.get("upload_mime"));
            dao.gradeResult(answerId,result.readable()?result.score():null,result.transcript(),result.feedback(),result.readable()?"AI_GRADED":"MANUAL_REQUIRED");
        }catch(Exception e){
            dao.gradeResult(answerId,null,"","Chưa chấm được bằng AI. Ảnh đã được lưu; em có thể thử lại sau. Không ghi điểm 0.","RETRY");
            if(!(e instanceof AIClient.AIException))throw e;
        }
    }
}
