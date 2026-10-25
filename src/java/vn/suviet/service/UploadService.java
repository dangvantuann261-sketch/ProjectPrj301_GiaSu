package vn.suviet.service;
import jakarta.servlet.http.Part;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import vn.suviet.util.Config;
public final class UploadService {
    public record Image(byte[] bytes,String mime,String extension){
    }
    public Image validate(Part part)throws IOException{
        if(part==null||part.getSize()<1||part.getSize()>5*1024*1024)throw new IllegalArgumentException("Ảnh phải có dung lượng từ 1 byte đến 5 MB.");
        String name=Objects.toString(part.getSubmittedFileName(),"").toLowerCase(Locale.ROOT);
        if(!name.matches(".*\\.(jpg|jpeg|png)$"))throw new IllegalArgumentException("Chỉ chấp nhận JPG hoặc PNG.");
        byte[] data;
        try(InputStream in=part.getInputStream()){
            data=in.readNBytes(5*1024*1024+1);
        }
        if(data.length>5*1024*1024)throw new IllegalArgumentException("Ảnh vượt 5 MB.");
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(data))){
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IllegalArgumentException("Tệp không phải ảnh hợp lệ.");
            ImageReader reader=readers.next();
            try{
                reader.setInput(input);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","png").contains(format))throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPG hoặc PNG thật.");
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||(long)w*h>16000000)throw new IllegalArgumentException("Ảnh tối đa 16 triệu điểm ảnh.");
                if(reader.read(0)==null)throw new IllegalArgumentException("Ảnh bị hỏng.");
                return new Image(data,format.equals("png")?"image/png":"image/jpeg",format.equals("png")?".png":".jpg");
            }
            finally{
                reader.dispose();
            }
        }
    }
    public Path directory()throws IOException{
        Path path=Path.of(Config.require("uploads.dir"));
        if(!path.isAbsolute())throw new IllegalStateException("uploads.dir phải là đường dẫn tuyệt đối");
        Files.createDirectories(path);
        return path.normalize();
    }
    public String save(Image image)throws IOException{
        String name=UUID.randomUUID()+image.extension();
        Files.write(directory().resolve(name),image.bytes(),StandardOpenOption.CREATE_NEW);
        return name;
    }
    public Path resolve(String name)throws IOException{
        if(name==null||!name.matches("[0-9a-f-]{36}\\.(png|jpg)"))throw new IllegalArgumentException("Ảnh không hợp lệ.");
        return directory().resolve(name);
    }
    public void remove(String name)throws IOException{
        Files.deleteIfExists(resolve(name));
    }
}
