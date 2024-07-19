package com.example.projetTechnique.service;

import com.example.projetTechnique.model.Post;
import com.example.projetTechnique.model.User;
import com.example.projetTechnique.repository.PostRepository;
import com.example.projetTechnique.repository.UserRepository;
import com.example.projetTechnique.utilities.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Optional;
@Service
public class PostService {
    @Autowired
    private PostRepository postRepository;

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private JwtUtil jwtUtil;

    private static final String UPLOAD_DIR = "src/main/resources/static/posts";

    public ResponseEntity<?> createPost(Post post, String token) {
        String jwtToken = token.substring(7);
        Long loggedInUserId = userService.getLoggedInUserId(jwtToken);
        if (loggedInUserId != null) {
            User loggedInUser = userService.findUserById(loggedInUserId);
            post.setUser(loggedInUser);
            post.setDateCreation(new Date());


            postRepository.save(post);
            return ResponseEntity.status(HttpStatus.CREATED).body(post);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Unauthorized\"}");
        }
    }

    public ResponseEntity<?> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        if (!posts.isEmpty()) {
            return ResponseEntity.ok(posts);
        } else {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("{\"message\":\"No posts available\"}");
        }
    }

    public ResponseEntity<?> getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post not found with id: " + id));
        if(post!=null){
            return ResponseEntity.ok(post);
        }
        else{
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"message\":\"Post not found with id: " + id + "\"}");
        }
    }

    public ResponseEntity<?> deletePost(Long id,String token ) {
            Long userId = userService.getLoggedInUserId(token);
            User user = userService.findUserById(userId);

            Post post = postRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Post not found with id: " + id));
            if (post.getUser() == null) {
                throw new IllegalStateException("Post does not have an associated user");
            }
            if (!post.getUser().equals(user)) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Failed to delete post");
            }
            postRepository.delete(post);
            Post TestPost=postRepository.findById(id).get();
            if(TestPost==null){
                return ResponseEntity.ok("{\"message\":\"Post deleted successfully\"}");
            }
            else{
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Failed to delete post");
            }
    }

    public ResponseEntity<?> uploadImage(Long postId, MultipartFile imageFile, String token) {
        String jwtToken = jwtUtil.extractToken(token);
        Long loggedInUserId = userService.getLoggedInUserId(jwtToken);
        if (loggedInUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Unauthorized Or user Not Found\"}");
        }

        Optional<Post> optionalPost = postRepository.findById(postId);
        if (optionalPost.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"message\":\"Post not found\"}");
        }

        Post post = optionalPost.get();
        if (!post.getUser().getId().equals(loggedInUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"message\":\"You are not allowed to upload an image for this post\"}");
        }

        if (imageFile == null || imageFile.isEmpty() ||
                (!imageFile.getContentType().equals("image/jpeg") &&
                        !imageFile.getContentType().equals("image/jpg") &&
                        !imageFile.getContentType().equals("image/png"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\":\"Invalid image file\"}");
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String originalFilename = imageFile.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            String uniqueFilename = "post_" + postId + "_" + System.currentTimeMillis() + fileExtension;

            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String imagePath = "posts/" + uniqueFilename;
            post.setImage(imagePath);
            postRepository.save(post);

            return ResponseEntity.ok("{\"message\":\"Image uploaded successfully\", \"imagePath\":\"" + imagePath + "\"}");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Could not upload the image\"}");
        }
    }


    public ResponseEntity<?> updatePost(Long idPost, Post updatedPost, MultipartFile imageFile) {
            Optional<Post> optionalPost = postRepository.findById(idPost);
            if (optionalPost.isPresent()) {
                Post existingPost = optionalPost.get();
                Post TestPost=existingPost;

                if (updatedPost.getImage() != null && !updatedPost.getImage().isEmpty()) {
                    existingPost.setImage(updatedPost.getImage());
                }
                if (updatedPost.getContenu() != null && !updatedPost.getContenu().isEmpty()) {
                    existingPost.setContenu(updatedPost.getContenu());
                }
                if (updatedPost.getDateCreation() != null) {
                    existingPost.setDateCreation(updatedPost.getDateCreation());
                }
                if (updatedPost.getUser() != null) {
                    existingPost.setUser(updatedPost.getUser());
                }

                if (imageFile != null && !imageFile.isEmpty()) {
                    String imagePath = fileStorageService.store(imageFile);
                    existingPost.setImage(imagePath);
                }

                postRepository.save(existingPost);
                if(existingPost!=TestPost){
                    return ResponseEntity.ok(existingPost);
                }
                else{
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Failed to update post\"}");
                }

            }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Failed to update post\"}");
    }
}
