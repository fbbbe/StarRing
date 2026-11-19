package com.example.starring.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.starring.common.ErrorMessages;
import com.example.starring.data.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

/**
 * 로그인/회원가입 요청을 Activity 대신 들고 있는 ViewModel.
 *
 * 왜 필요한가: Activity에 묶인 콜백(addOnCompleteListener(this, ...))은 화면 회전으로
 * Activity가 다시 만들어지면 사라진다. 그러면 가입은 완료됐는데 화면은 가입 폼에 멈춰 있게 됨.
 * ViewModel은 회전에도 살아 있으므로 결과를 새 Activity에 그대로 전달한다.
 */
public class AuthViewModel extends ViewModel {

    private final MutableLiveData<AuthUiState> state = new MutableLiveData<>(AuthUiState.idle());
    private final UserRepository userRepository = new UserRepository();

    public LiveData<AuthUiState> state() {
        return state;
    }

    private boolean isLoading() {
        AuthUiState s = state.getValue();
        return s != null && s.status == AuthUiState.Status.LOADING;
    }

    public void login(String email, String password) {
        if (isLoading()) {
            return; // 중복 요청 방지
        }
        state.setValue(AuthUiState.loading());
        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> state.setValue(task.isSuccessful()
                        ? AuthUiState.success()
                        : AuthUiState.error(ErrorMessages.toMessage(task.getException()))));
    }

    public void signUp(String email, String password, String nickname) {
        if (isLoading()) {
            return;
        }
        state.setValue(AuthUiState.loading());
        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    FirebaseUser user = task.isSuccessful() ? task.getResult().getUser() : null;
                    if (user == null) {
                        state.setValue(AuthUiState.error(ErrorMessages.toMessage(task.getException())));
                        return;
                    }
                    saveProfile(user, email, nickname);
                });
    }

    /**
     * Auth 프로필 displayName에도 닉네임을 넣어둔다.
     * → users 문서 저장이 실패해도 Splash가 displayName으로 문서를 복구할 수 있다.
     */
    private void saveProfile(FirebaseUser user, String email, String nickname) {
        UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                .setDisplayName(nickname)
                .build();
        user.updateProfile(profile)
                // displayName 실패 여부와 상관없이 users 문서 저장 진행
                .continueWithTask(t -> userRepository.createUser(user.getUid(), email, nickname))
                .addOnCompleteListener(t -> state.setValue(t.isSuccessful()
                        ? AuthUiState.success()
                        // 계정은 이미 만들어졌으므로 Splash에서 users 문서 복구 후 분기
                        : AuthUiState.successNeedsRecovery()));
    }
}
