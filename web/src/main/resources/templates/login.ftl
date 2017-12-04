<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <meta name="description" content="Bootstrap Admin Template">
    <meta name="keywords" content="app, responsive, jquery, bootstrap, dashboard, admin">
    <title>eScienceCloud - Login</title>
    <!-- build:css(../app) css/vendor-user.css-->
    <!-- Animate.CSS-->
    <link rel="stylesheet" href="/vendor/animate.css/animate.css">
    <!-- Bootstrap-->
    <link rel="stylesheet" href="/vendor/bootstrap/dist/css/bootstrap.min.css">
    <!-- Ionicons-->
    <link rel="stylesheet" href="/vendor/ionicons/css/ionicons.css">
    <!-- Material Colors-->
    <link rel="stylesheet" href="/vendor/material-colors/dist/colors.css">
    <!-- endbuild-->
    <!-- Application styles-->
    <link rel="stylesheet" href="/css/app.css">
</head>
<body>
<div class="layout-container">
    <div class="page-container bg-blue-grey-900">
        <div class="container-full">
            <div class="container container-xs">
                <img src="" data-svg-replace="esciencecloud_logo_text.svg" alt="MenuItem"
                     class="mv-lg block-center img-responsive thumb64">

                <!--<img src="img/logo.png" class="mv-lg block-center img-responsive thumb64">-->
                <form id="user-login" method="post" action="login" name="loginForm" novalidate="" class="card b0 form-validate">
                    <div class="card-offset pb0">
                        <!--<div class="card-offset-item text-right"><a href="signup.html" class="btn-raised btn btn-info btn-circle btn-lg"><em class="ion-person-add"></em></a></div>
                        <div class="card-offset-item text-right hidden">
                          <div class="btn btn-success btn-circle btn-lg"><em class="ion-checkmark-round"></em></div>
                        </div>-->
                    </div>
                    <div class="card-heading">
                        <div class="card-title text-center">Login</div>
                    </div>
                    <div class="card-body">
                        <div class="mda-form-group float-label mda-input-group">
                            <div class="mda-form-control">
                                <input type="text" name="accountName" required="" class="form-control">
                                <div class="mda-form-control-line"></div>
                                <label>Username</label>
                            </div><span class="mda-input-group-addon"><em class="ion-ios-email-outline icon-lg"></em></span>
                        </div>
                        <div class="mda-form-group float-label mda-input-group">
                            <div class="mda-form-control">
                                <input type="password" name="accountPassword" required="" class="form-control">
                                <div class="mda-form-control-line"></div>
                                <label>Password</label>
                            </div><span class="mda-input-group-addon"><em class="ion-ios-locked-outline icon-lg"></em></span>
                        </div>
                    </div>
                    <button type="submit" class="btn btn-primary btn-flat">Authenticate</button>
                </form>
                <div class="text-center text-sm"><a href="/recover.html" class="text-inherit">Forgot password?</a></div>
            </div>
        </div>
    </div>
</div>
<!-- build:js(../app) js/vendor-user.js-->
<!-- Modernizr-->
<script src="/vendor/modernizr/modernizr.custom.js"></script>
<!-- jQuery-->
<script src="/vendor/jquery/dist/jquery.js"></script>
<!-- Bootstrap-->
<script src="/vendor/bootstrap/dist/js/bootstrap.js"></script>
<!-- jQuery Browser-->
<script src="/vendor/jquery.browser/dist/jquery.browser.js"></script>
<!-- Material Colors-->
<script src="/vendor/material-colors/dist/colors.js"></script>
<!-- jQuery Form Validation-->
<script src="/vendor/jquery-validation/dist/jquery.validate.js"></script>
<script src="/vendor/jquery-validation/dist/additional-methods.js"></script>
<!-- endbuild-->
<!-- App script-->
<script src="/js/app.js"></script>
</body>
</html>