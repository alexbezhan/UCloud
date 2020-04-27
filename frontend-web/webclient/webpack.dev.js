var webpack = require("webpack");
var webpackMerge = require("webpack-merge");
const CopyWebpackPlugin = require("copy-webpack-plugin");
const MiniCSSExtractPlugin = require("mini-css-extract-plugin");
var commonConfig = require("./webpack.config.js");
var path = require("path");
var {DEV_SITE} = require("./site.config.json")

module.exports = webpackMerge(commonConfig, {
    devtool: "eval-source-map",

    mode: "development",


    entry: {
        app: "./app/App.tsx"
    },

    output: {
        path: path.join(process.cwd(), "/dist"),
        publicPath: "http://localhost:9000/",
        filename: "[name].js",
    },

    plugins: [
        // Enables Hot Module Replacement, otherwise known as HMR.
        // Note: HMR should never be used in production. 
        new webpack.HotModuleReplacementPlugin(),
        // new MiniCSSExtractPlugin({
        //     filename: "[name].css",
        //     chunkFilename: "[id].css"
        // }),
        // Copies individual files or entire directories to the build directory
        new CopyWebpackPlugin([{
            from: "mock-api",
            to: "mock-api",
            context: path.join(__dirname, "app")
        }])
    ],

    devServer: {
        disableHostCheck: true,
        historyApiFallback: true,
        stats: "minimal",
        contentBase: path.join(process.cwd(), "/dist"),
        index: "app/index.html",
        headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
            "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization"
        },
        hot: true,
        inline: true,
        proxy: [{
            context: ["/auth", "/api"],
            target: process.env["local_dev"] !== undefined ? `http://localhost:8080` : `https://${DEV_SITE}`,
            secure: false,
            changeOrigin: true,
            ws: true,
            onProxyRes(proxyRes, req, res) {
                if ("set-cookie" in proxyRes.headers) {
                    for (let i = 0; i < proxyRes.headers["set-cookie"].length; i++) {
                        proxyRes.headers["set-cookie"][i] = proxyRes.headers["set-cookie"][i].replace(/Secure;/g, "");
                    }
                }
                delete proxyRes.headers["strict-transport-security"];
            }
        }]
    }
});
